package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.HotSearchWord;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteVideoInfo;
import com.lzz.lime_server.dto.response.UserSearchResult;
import com.lzz.lime_server.entity.Note;
import com.lzz.lime_server.entity.NoteLike;
import com.lzz.lime_server.entity.UserFollow;
import com.lzz.lime_server.mapper.NoteLikeMapper;
import com.lzz.lime_server.mapper.NoteMapper;
import com.lzz.lime_server.mapper.UserFollowMapper;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 搜索服务实现类
 * <p>负责笔记搜索、用户搜索、搜索联想、热搜榜与热搜上报。
 * 热搜统计存 Redis ZSET，按天轮转 key（hot:search:yyyyMMdd），TTL 2 天，只保留 Top50。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final NoteMapper noteMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final StringRedisTemplate redisTemplate;

    // 热搜 ZSET 的 key 前缀，完整 key 为 hot:search:yyyyMMdd
    private static final String HOT_KEY_PREFIX = "hot:search:";
    // 热搜 key 的日期格式：yyyyMMdd
    private static final DateTimeFormatter HOT_KEY_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    // 热搜 key 的过期时间（天），跨天自动轮转
    private static final long HOT_KEY_TTL_DAYS = 2;
    // 热搜榜最大保留成员数，超出部分在上报时裁剪
    private static final int HOT_KEY_MAX_MEMBERS = 50;

    /**
     * 关键词搜索笔记，游标分页。
     * <p>召回 = 标题/正文命中（FULLTEXT ngram）或作者昵称/handle 命中（召回其已发布笔记，
     * 统一以笔记卡片返回）。单字或含空格关键词自动走 LIKE 兜底
     * （ngram_token_size=2 单字不命中；空格会拆词违背整串匹配）。</p>
     * <p>type 过滤笔记类型：all=全部，image=图文，video=视频。
     * 排序规则：主排序键（sort 决定）DESC → 发布时间 DESC → id DESC；
     * 游标为 "{sortScore}:{createTimeMs}:{id}" 三段复合游标，首次查询传 null。
     * 每次多查一条用于判断 hasMore，同时批量 IN 查询填充当前用户的 liked 状态。</p>
     *
     * @param keyword       搜索关键词，trim 后非空、不超过 50 个字符
     * @param sort          排序方式：composite（综合）/ latest / likes / comments / favs
     * @param within        发布时间范围：all / day / week / halfYear，与 sort 自由组合
     * @param type          笔记类型过滤：all / image / video
     * @param cursor        上一页 nextCursor，原样回传；格式非法抛业务异常
     * @param size          每页条数
     * @param currentUserId 当前登录用户 id，用于填充 liked
     * @return 笔记卡片分页结果
     * @throws BusinessException 关键词为空/超长、within 或 cursor 非法时抛出
     */
    @Override
    public CursorPage<NoteFeedResponse> searchNotes(String keyword, String sort, String within, String type, String cursor, int size, Long currentUserId) {
        String kw = normalizeKeyword(keyword);
        String likePattern = "%" + escapeLike(keyword.trim()) + "%";
        LocalDateTime fromTime = resolveFromTime(within);
        Integer noteTypeFilter = switch (type) {
            case "image" -> Note.TYPE_TEXT;
            case "video" -> Note.TYPE_VIDEO;
            default -> null;// all：不限制类型
        };

        // 游标解析：统一为 "{sortScore}:{createTimeMs}:{id}" 复合游标
        Long cursorScore = null;
        Long cursorTimeMs = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split(":");
                cursorScore = Long.parseLong(parts[0]);
                cursorTimeMs = Long.parseLong(parts[1]);
                cursorId = Long.parseLong(parts[2]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new BusinessException("游标格式非法");
            }
        }

        // 多查一条判断是否还有下一页
        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectSearch(
                kw, likePattern, LocalDate.now(), fromTime, sort, noteTypeFilter,
                cursorScore, cursorTimeMs, cursorId, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(row -> {
            NoteFeedResponse item = new NoteFeedResponse();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverImage(row.getCoverImage());
            item.setLikeCount(row.getLikeCount());
            item.setNoteType(row.getNoteType());
            if (row.getNoteType() != null && row.getNoteType() == Note.TYPE_VIDEO) {
                item.setVideo(NoteVideoInfo.of(row.getVideoDurationMs(), row.getVideoWidth(),
                        row.getVideoHeight(), row.getVideoPlayUrl(), null));
            }

            NoteFeedResponse.AuthorBrief author = new NoteFeedResponse.AuthorBrief();
            author.setId(row.getAuthorId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            item.setAuthor(author);
            return item;
        }).toList();

        // 批量查询当前用户对这批笔记的点赞状态，一次 IN 查询代替 N 次单条查询
        if (!items.isEmpty()) {
            List<Long> noteIds = items.stream().map(NoteFeedResponse::getId).toList();
            Set<Long> likedNoteIds = noteLikeMapper.selectList(
                            new LambdaQueryWrapper<NoteLike>()
                                    .eq(NoteLike::getUserId, currentUserId)
                                    .in(NoteLike::getNoteId, noteIds))
                    .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
            items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
        }

        // 批量填充卡片作者的关注状态
        List<Long> authorIds = items.stream()
                .map(NoteFeedResponse::getAuthor)
                .filter(author -> author != null)
                .map(NoteFeedResponse.AuthorBrief::getId)
                .distinct().toList();
        if (!authorIds.isEmpty()) {
            Set<Long> followedIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFollowerId, currentUserId)
                                    .in(UserFollow::getFolloweeId, authorIds))
                    .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
            Set<Long> followedBackIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFolloweeId, currentUserId)
                                    .in(UserFollow::getFollowerId, authorIds))
                    .stream().map(UserFollow::getFollowerId).collect(Collectors.toSet());
            items.forEach(item -> {
                NoteFeedResponse.AuthorBrief author = item.getAuthor();
                if (author != null) {
                    author.setIsFollowing(followedIds.contains(author.getId()));
                    author.setIsFollowedBack(followedBackIds.contains(author.getId()));
                }
            });
        }

        String nextCursor = null;
        if (hasMore) {
            NoteMapper.NoteFeedRow last = rows.getLast();
            nextCursor = last.getSortScore() + ":" + last.getCreateTimeMs() + ":" + last.getId();
        }
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 搜索用户（昵称/handle 匹配），匹配度优先排序，游标分页。
     * <p>排序：matchRank DESC（精确=3 &gt; 前缀=2 &gt; 包含=1）→ 已发布笔记数 DESC → id DESC；
     * 游标为 "{matchRank}:{noteCount}:{id}" 三段复合游标。结果中 isMe 标记是否为当前登录用户。</p>
     *
     * @param keyword       搜索关键词，trim 后非空、不超过 50 个字符
     * @param cursor        上一页 nextCursor，原样回传；格式非法抛业务异常
     * @param size          每页条数
     * @param currentUserId 当前登录用户 id，用于计算 isMe
     * @return 用户卡片分页结果
     * @throws BusinessException 关键词为空/超长、cursor 非法时抛出
     */
    @Override
    public CursorPage<UserSearchResult> searchUsers(String keyword, String cursor, int size, Long currentUserId) {
        String kw = requireKeyword(keyword);
        String prefix = escapeLike(kw) + "%";
        String likePattern = "%" + escapeLike(kw) + "%";

        Long cursorId = null;
        Integer cursorRank = null;
        Long cursorNoteCount = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = cursor.split(":");
                cursorRank = Integer.parseInt(parts[0]);
                cursorNoteCount = Long.parseLong(parts[1]);
                cursorId = Long.parseLong(parts[2]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new BusinessException("游标格式非法");
            }
        }

        // 多查一条判断是否还有下一页
        List<UserMapper.UserSearchRow> rows = userMapper.selectSearchUsers(
                kw, prefix, likePattern, cursorRank, cursorNoteCount, cursorId, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<UserSearchResult> items = rows.stream().map(row -> {
            UserSearchResult item = new UserSearchResult();
            item.setId(row.getId());
            item.setNickname(row.getNickname());
            item.setHandle(row.getHandle());
            item.setAvatar(row.getAvatar());
            item.setIsMe(row.getId().equals(currentUserId));
            return item;
        }).toList();

        // 批量填充关注状态
        List<Long> userIds = items.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsMe()))
                .map(UserSearchResult::getId).toList();
        if (!userIds.isEmpty()) {
            Set<Long> followedIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFollowerId, currentUserId)
                                    .in(UserFollow::getFolloweeId, userIds))
                    .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
            Set<Long> followedBackIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFolloweeId, currentUserId)
                                    .in(UserFollow::getFollowerId, userIds))
                    .stream().map(UserFollow::getFollowerId).collect(Collectors.toSet());
            items.forEach(item -> {
                if (!Boolean.TRUE.equals(item.getIsMe())) {
                    item.setIsFollowing(followedIds.contains(item.getId()));
                    item.setIsFollowedBack(followedBackIds.contains(item.getId()));
                }
            });
        }

        String nextCursor = null;
        if (hasMore) {
            UserMapper.UserSearchRow last = rows.getLast();
            nextCursor = last.getMatchRank() + ":" + last.getNoteCount() + ":" + last.getId();
        }
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 搜索联想（输入中的实时下拉提示）。
     * <p>来源 = 已发布笔记标题（前缀匹配，按热度取）+ 当日热搜词中前缀匹配的，
     * LinkedHashSet 合并去重（标题优先），最终截断到 size 条。不包含用户。</p>
     *
     * @param q    已输入片段；为 null 或空白时直接返回空列表（清空输入框是正常场景，不抛异常）
     * @param size 最多返回条数
     * @return 联想词列表
     */
    @Override
    public List<String> suggest(String q, int size) {
        if (q == null || q.isBlank()) return List.of();
        String trimmed = q.trim();

        List<String> titles = noteMapper.selectSuggestTitles(escapeLike(trimmed) + "%", size);
        Set<String> result = new LinkedHashSet<>(titles);

        Set<String> hotMembers = redisTemplate.opsForZSet().range(hotKey(), 0, -1);
        if (hotMembers != null) {
            for (String w : hotMembers) {
                if (result.size() >= size) break;
                if (w != null && w.startsWith(trimmed)) result.add(w);
            }
        }
        return result.stream().limit(size).toList();
    }

    /**
     * 当日热搜榜，按搜索点击次数降序。
     * <p>读取当日 ZSET（hot:search:yyyyMMdd）的 Top size 成员及其分数。
     * key 不存在或为空时返回空列表。</p>
     *
     * @param size 最多返回条数
     * @return 热搜词及点击次数列表
     */
    @Override
    public List<HotSearchWord> hotSearchWords(int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(hotKey(), 0, size - 1);
        if (tuples == null || tuples.isEmpty()) return List.of();
        return tuples.stream().map(tuple -> {
            HotSearchWord word = new HotSearchWord();
            word.setKeyword(tuple.getValue());
            word.setCount(tuple.getScore() != null ? tuple.getScore().longValue() : 0);
            return word;
        }).toList();
    }

    /**
     * 上报一次搜索行为，当日热搜计数 +1。
     * <p>由前端在用户确认搜索时调用一次（翻页不调用，避免重复计数）。
     * 每次上报顺带刷新 key 的 TTL（2 天）并将榜单裁剪到 Top50，
     * 保证榜单体积可控、联想接口的 ZSET 全量扫描成本恒定。</p>
     *
     * @param keyword 用户实际搜索的关键词；null 或空白时幂等忽略
     */
    @Override
    public void reportSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) return; // 幂等忽略
        String key = hotKey();
        redisTemplate.opsForZSet().incrementScore(key, keyword.trim(), 1.0);
        redisTemplate.expire(key, HOT_KEY_TTL_DAYS, TimeUnit.DAYS);
        // 只保留 Top50，控制榜单与联想扫描成本
        redisTemplate.opsForZSet().removeRange(key, 0, -(HOT_KEY_MAX_MEMBERS + 1));
    }

    /**
     * 当日热搜 ZSET 的 key，格式 hot:search:yyyyMMdd（按天轮转）。
     *
     * @return 当日热搜 key
     */
    private String hotKey() {
        return HOT_KEY_PREFIX + LocalDate.now().format(HOT_KEY_DATE);
    }

    /**
     * 归一化关键词，决定走 FULLTEXT 还是 LIKE 召回。
     * <p>ngram_token_size=2 是只读参数：单字关键词 MATCH 不命中；
     * 含空格的关键词会被 FULLTEXT 拆词（违背整串匹配语义）。
     * 这两类情况返回 null，由 SQL 层走 LIKE 兜底（此时综合分退化为热度分）。</p>
     *
     * @param keyword 原始关键词
     * @return 可用于 FULLTEXT MATCH 的关键词；单字或含空格时返回 null
     * @throws BusinessException 关键词为空或超长时抛出
     */
    private String normalizeKeyword(String keyword) {
        String trimmed = requireKeyword(keyword);
        boolean hasSpace = trimmed.codePoints().anyMatch(Character::isWhitespace);
        return (trimmed.codePointCount(0, trimmed.length()) >= 2 && !hasSpace) ? trimmed : null;
    }

    /**
     * 校验并归一化关键词：trim 后必须非空、不超过 50 个字符（按 code point 计）。
     *
     * @param keyword 原始关键词
     * @return trim 后的关键词
     * @throws BusinessException 关键词为空白或超过 50 个字符时抛出
     */
    private String requireKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("搜索关键词不能为空");
        }
        String trimmed = keyword.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > 50) {
            throw new BusinessException("搜索关键词不能超过 50 个字符");
        }
        return trimmed;
    }

    /**
     * 解析发布时间范围为起始时间，供 SQL 的 create_time &gt;= fromTime 过滤。
     *
     * @param within 时间范围：all / day / week / halfYear
     * @return 起始时间；all 或 null 表示不限（返回 null，SQL 层跳过该条件）
     * @throws BusinessException within 不在白名单时抛出
     */
    private LocalDateTime resolveFromTime(String within) {
        if (within == null || "all".equals(within)) return null;
        LocalDateTime now = LocalDateTime.now();
        return switch (within) {
            case "day" -> now.minusDays(1);
            case "week" -> now.minusDays(7);
            case "halfYear" -> now.minusMonths(6);
            default -> throw new BusinessException("within 参数非法，可选值：all / day / week / halfYear");
        };
    }

    /**
     * 转义 LIKE 通配符（\、%、_），避免用户输入被当作通配符导致误匹配。
     *
     * @param s 原始关键词
     * @return 转义后可安全拼入 LIKE 模式的字符串
     */
    private String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
