package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzz.lime_server.common.ResultCode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NoteDetailResponse;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteResponse;
import com.lzz.lime_server.entity.*;
import com.lzz.lime_server.mapper.*;
import com.lzz.lime_server.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * 笔记接口实现类
 * <p>负责笔记的发布、更新、信息流获取、点赞、收藏等</p>
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final NoteFavMapper noteFavMapper;
    private final NoteViewMapper noteViewMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private static final String LIKE_COUNT_PREFIX = "note:like:";
    private static final String FAV_COUNT_PREFIX  = "note:fav:";
    private static final long   COUNT_TTL_MINUTES = 10;

    /**
     * 发布图文笔记
     * <p>事务控制:确保笔记主体与图片数据同时成功或同时回滚</p>
     *
     * @param userId  当前登录用户的ID（从JWT中解析获取）
     * @param request 发布笔记的请求参数
     * @return 发布成功后的笔记响应数据
     * @throws BusinessException 当标题和正文同时为空时抛出业务异常
     */
    @Override
    @Transactional
    public NoteResponse publishNote(Long userId, PublishNoteRequest request) {
        if (!StringUtils.hasText(request.getTitle()) && !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("标题和正文不能同时为空");
        }

        // 构建并保存笔记主体信息
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        note.setLikeCount(0);
        note.setFavCount(0);
        note.setViewCount(0);
        noteMapper.insert(note);

        // 批量构建并保存笔记关联的图片数据
        List<NoteImage> images = request.getImages().stream().map(item -> {
            NoteImage img = new NoteImage();
            img.setNoteId(note.getId());// 绑定刚生成的笔记ID
            img.setUrl(item.getUrl());
            img.setSortOrder(item.getSortOrder());
            return img;
        }).toList();
        images.forEach(noteImageMapper::insert);// 逐条插入图片

        return toResponse(note, images);
    }

    /**
     * 获取指定用户的笔记列表，游标分页。
     * <p>草稿（statusVal=0）仅限本人查看，否则抛出业务异常。</p>
     *
     * @param targetUserId  目标用户 ID
     * @param statusVal     0=草稿，1=已发布
     * @param cursor        游标（上一页最后一条笔记的 ID），首次传 null
     * @param size          每页条数
     * @param currentUserId 当前登录用户 ID
     */
    @Override
    public CursorPage<NoteFeedResponse> getUserNotes(Long targetUserId, int statusVal, Long cursor, int size, Long currentUserId) {
        // 草稿验证是否为当前登录用户是否为目标用户
        if (statusVal == 0 && !targetUserId.equals(currentUserId)) {
            throw new BusinessException("无权查看他人草稿");
        }

        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectUserNotes(targetUserId, statusVal, cursor, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(row -> {
            NoteFeedResponse item = new NoteFeedResponse();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverImage(row.getCoverImage());
            item.setLikeCount(row.getLikeCount());
            item.setStatus(row.getStatus());
            // 浏览量仅本人可见，非本人保持 null（序列化时不输出）
            if (targetUserId.equals(currentUserId)) {
                item.setViewCount(row.getViewCount());
            }

            NoteFeedResponse.AuthorBrief author = new NoteFeedResponse.AuthorBrief();
            author.setId(row.getAuthorId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            item.setAuthor(author);
            return item;
        }).toList();

        if (!items.isEmpty()) {
            List<Long> noteIds = items.stream().map(NoteFeedResponse::getId).toList();
            Set<Long> likedNoteIds = noteLikeMapper.selectList(
                            new LambdaQueryWrapper<NoteLike>()
                                    .eq(NoteLike::getUserId, currentUserId)
                                    .in(NoteLike::getNoteId, noteIds))
                    .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
            items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
        }

        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }


    /**
     * 获取笔记信息流数据，游标分页。
     *
     * @param cursor 游标，即上一页最后一条笔记的ID。首次请求时传 null，后续请求传入返回的 nextCursor
     * @param size   每页期望获取的笔记条数
     * @param userId 当前登录用户 ID，用于批量判断每条笔记的点赞状态
     * @return       包含笔记列表、下一页游标及是否有更多数据的分页对象
     */
    @Override
    public CursorPage<NoteFeedResponse> getFeed(Long cursor, int size, Long userId) {
        // 多查一条数据，判断是否还有下一页
        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectFeed(cursor, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }// 丢弃多的一条，保留当前页所需的数据

        // 将数据库返回的扁平化投影对象转换为面向前端的结构化响应对象
        List<NoteFeedResponse> items = rows.stream().map(row -> {
            NoteFeedResponse item = new NoteFeedResponse();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverImage(row.getCoverImage());
            item.setLikeCount(row.getLikeCount());

            NoteFeedResponse.AuthorBrief author = new NoteFeedResponse.AuthorBrief();
            author.setId(row.getAuthorId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            item.setAuthor(author);
            return item;
        }).toList();

        // fix: 批量查询当前用户对这批笔记的点赞状态，一次 IN 查询代替 N 次单条查询
        if (!items.isEmpty()) {
            List<Long> noteIds = items.stream().map(NoteFeedResponse::getId).toList();
            Set<Long> likedNoteIds = noteLikeMapper.selectList(
                            new LambdaQueryWrapper<NoteLike>()
                                    .eq(NoteLike::getUserId, userId)
                                    .in(NoteLike::getNoteId, noteIds))
                    .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
            items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
        }

        // 还有下一页，将当前页最后一条笔记的 ID 作为下一次请求的游标
        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }


    /**
     * 构建笔记详情响应对象。
     *
     * @param note       笔记实体对象，包含标题、内容、状态及时间等基础信息
     * @param images     笔记关联的图片列表
     * @param author     笔记作者的用户信息（允许为 null）
     * @param likeCount  笔记的点赞总数
     * @param favCount   笔记的收藏总数
     * @param liked      当前登录用户是否已点赞该笔记
     * @param favorited  当前登录用户是否已收藏该笔记
     * @return 组装完成的笔记详情响应对象
     */
    private NoteDetailResponse buildDetailResponse(Note note, List<NoteImage> images, User author,
                                                   int likeCount, int favCount,
                                                   boolean liked, boolean favorited) {
        NoteDetailResponse resp = new NoteDetailResponse();
        resp.setId(note.getId());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setStatus(note.getStatus());
        resp.setLikeCount(likeCount);
        resp.setFavCount(favCount);
        resp.setViewCount(note.getViewCount());
        resp.setLiked(liked);
        resp.setFavorited(favorited);
        resp.setCreateTime(note.getCreateTime());
        resp.setUpdateTime(note.getUpdateTime());

        resp.setImages(images.stream().map(img -> {
            NoteDetailResponse.ImageItem item = new NoteDetailResponse.ImageItem();
            item.setId(img.getId());
            item.setUrl(img.getUrl());
            item.setSortOrder(img.getSortOrder());
            return item;
        }).toList());

        if (author != null) {
            NoteDetailResponse.AuthorInfo info = new NoteDetailResponse.AuthorInfo();
            info.setId(author.getId());
            info.setNickname(author.getNickname());
            info.setAvatar(author.getAvatar());
            resp.setAuthor(info);
        }

        return resp;
    }


    /**
     * 获取笔记详情。
     * <p>点赞/收藏计数优先读 Redis（TTL 10 分钟），缓存未命中时从 DB 取值并回填缓存；
     * 写操作（点赞/取消）主动删除 Redis key，保证最终一致性。</p>
     *
     * @param noteId        笔记 ID
     * @param currentUserId 当前登录用户 ID，用于判断 liked / favorited 状态
     * @return 笔记详情响应，包含完整图文、作者信息、互动计数及当前用户状态
     */
    @Override
    public NoteDetailResponse getNoteDetail(Long noteId, Long currentUserId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }

        // 按 sort_order 升序加载所有图片
        List<NoteImage> images = noteImageMapper.selectList(
                new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, noteId)
                        .orderByAsc(NoteImage::getSortOrder));

        User author = userMapper.selectById(note.getUserId());

        // 计数优先读 Redis，缓存未命中时从 note 记录取值并写入 Redis
        int likeCount = getCachedCount(LIKE_COUNT_PREFIX + noteId, note.getLikeCount());
        int favCount  = getCachedCount(FAV_COUNT_PREFIX  + noteId, note.getFavCount());

        // 查当前用户是否已点赞/收藏
        boolean liked = noteLikeMapper.selectCount(
                new LambdaQueryWrapper<NoteLike>()
                        .eq(NoteLike::getNoteId, noteId)
                        .eq(NoteLike::getUserId, currentUserId)) > 0;

        boolean favorited = noteFavMapper.selectCount(
                new LambdaQueryWrapper<NoteFav>()
                        .eq(NoteFav::getNoteId, noteId)
                        .eq(NoteFav::getUserId, currentUserId)) > 0;

        // 每次详情访问累计浏览量
        noteMapper.incrementViewCount(noteId);

        // 记录浏览历史,重复浏览同一笔记则更新时间，使其重新出现在历史顶部
        noteViewMapper.upsertView(currentUserId, noteId);

        return buildDetailResponse(note, images, author, likeCount, favCount, liked, favorited);
    }



    /**
     * 读取点赞/收藏计数
     *
     * @param key        Redis key
     * @param dbFallback selectById 已查到的 DB 计数值
     */
    private int getCachedCount(String key, int dbFallback) {
        String cached = redisTemplate.opsForValue().get(key);
        // 优先命中 Redis
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        // 缓存未命中：将 DB 值写入 Redis，设置过期时间
        redisTemplate.opsForValue().set(key, String.valueOf(dbFallback), COUNT_TTL_MINUTES, TimeUnit.MINUTES);
        return dbFallback;
    }


    /**
     * 点赞笔记（重复点赞直接返回）。
     *
     * @param noteId 笔记 ID
     * @param userId 当前用户 ID
     */
    @Override
    @Transactional
    public void likeNote(Long noteId, Long userId) {
        ensureNoteExists(noteId);

        boolean alreadyLiked = noteLikeMapper.selectCount(
                new LambdaQueryWrapper<NoteLike>()
                        .eq(NoteLike::getNoteId, noteId)
                        .eq(NoteLike::getUserId, userId)) > 0;
        if (alreadyLiked) return;// 幂等检查

        NoteLike like = new NoteLike();
        like.setNoteId(noteId);
        like.setUserId(userId);
        noteLikeMapper.insert(like);

        // 点赞数增加
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("like_count = like_count + 1"));

        // 写后删除缓存，下次读取时再重新加载
        redisTemplate.delete(LIKE_COUNT_PREFIX + noteId);
    }

    /**
     * 取消点赞，（未点赞时直接返回）。
     *
     * @param noteId 笔记 ID
     * @param userId 当前用户 ID
     */
    @Override
    @Transactional
    public void unlikeNote(Long noteId, Long userId) {
        int deleted = noteLikeMapper.delete(
                new LambdaQueryWrapper<NoteLike>()
                        .eq(NoteLike::getNoteId, noteId)
                        .eq(NoteLike::getUserId, userId));
        if (deleted == 0) return;// 未点赞，幂等返回
        
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("like_count = GREATEST(like_count - 1, 0)"));// 防止计数降为负数

        redisTemplate.delete(LIKE_COUNT_PREFIX + noteId);
    }

    /**
     * 收藏笔记，幂等操作。
     *
     * @param noteId 笔记 ID
     * @param userId 当前用户 ID
     */
    @Override
    @Transactional
    public void favoriteNote(Long noteId, Long userId) {
        ensureNoteExists(noteId);

        boolean alreadyFav = noteFavMapper.selectCount(
                new LambdaQueryWrapper<NoteFav>()
                        .eq(NoteFav::getNoteId, noteId)
                        .eq(NoteFav::getUserId, userId)) > 0;
        if (alreadyFav) return;

        NoteFav fav = new NoteFav();
        fav.setNoteId(noteId);
        fav.setUserId(userId);
        noteFavMapper.insert(fav);

        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("fav_count = fav_count + 1"));

        redisTemplate.delete(FAV_COUNT_PREFIX + noteId);
    }

    /**
     * 取消收藏，幂等操作。
     *
     * @param noteId 笔记 ID
     * @param userId 当前用户 ID
     */
    @Override
    @Transactional
    public void unfavoriteNote(Long noteId, Long userId) {
        int deleted = noteFavMapper.delete(
                new LambdaQueryWrapper<NoteFav>()
                        .eq(NoteFav::getNoteId, noteId)
                        .eq(NoteFav::getUserId, userId));
        if (deleted == 0) return;

        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("fav_count = GREATEST(fav_count - 1, 0)"));

        redisTemplate.delete(FAV_COUNT_PREFIX + noteId);
    }



    /**
     * 获取指定用户点赞过的笔记列表，游标分页。
     * <p>若目标用户已开启点赞隐私且当前用户非本人，抛出业务异常。</p>
     */
    @Override
    public CursorPage<NoteFeedResponse> getLikedNotes(Long targetUserId, Long cursor, int size, Long currentUserId) {
        if (!targetUserId.equals(currentUserId)) {
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) throw new BusinessException(ResultCode.NOT_FOUND);
            if (Boolean.TRUE.equals(targetUser.getLikePrivate())) {
                throw new BusinessException("该用户已开启点赞列表隐私");
            }
        }
        return queryInteractionNotes(
                noteMapper.selectLikedNotes(targetUserId, cursor, size + 1),
                size, currentUserId);
    }

    /**
     * 获取指定用户收藏的笔记列表，游标分页。
     * <p>若目标用户已开启收藏隐私且当前用户非本人，抛出业务异常。</p>
     */
    @Override
    public CursorPage<NoteFeedResponse> getFavoritedNotes(Long targetUserId, Long cursor, int size, Long currentUserId) {
        if (!targetUserId.equals(currentUserId)) {
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) throw new BusinessException(ResultCode.NOT_FOUND);
            if (Boolean.TRUE.equals(targetUser.getFavPrivate())) {
                throw new BusinessException("该用户已开启收藏列表隐私");
            }
        }
        return queryInteractionNotes(
                noteMapper.selectFavoritedNotes(targetUserId, cursor, size + 1),
                size, currentUserId);
    }

    /**
     * 获取当前用户的浏览历史，游标分页。
     * cursor 为上一页最后一条的浏览时间，首次传 null。
     * 每条笔记在历史中唯一，重复浏览时更新至顶部。
     */
    @Override
    public CursorPage<NoteFeedResponse> getViewedNotes(Long userId, Long cursor, int size) {
        return queryInteractionNotes(
                noteMapper.selectViewedNotes(userId, cursor, size + 1),
                size, userId);
    }

    /**
     * 将点赞/收藏/浏览历史查询结果转换为 CursorPage，并批量标记当前用户的点赞状态。
     * cursor 基于 note_like/note_fav 的 id（即操作时间顺序），而非 note.id。
     */
    private CursorPage<NoteFeedResponse> queryInteractionNotes(
            List<NoteMapper.NoteFeedRow> rows, int size, Long currentUserId) {
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(row -> {
            NoteFeedResponse item = new NoteFeedResponse();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverImage(row.getCoverImage());
            item.setLikeCount(row.getLikeCount());
            NoteFeedResponse.AuthorBrief author = new NoteFeedResponse.AuthorBrief();
            author.setId(row.getAuthorId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            item.setAuthor(author);
            return item;
        }).toList();

        if (!items.isEmpty()) {
            List<Long> noteIds = items.stream().map(NoteFeedResponse::getId).toList();
            Set<Long> likedNoteIds = noteLikeMapper.selectList(
                            new LambdaQueryWrapper<NoteLike>()
                                    .eq(NoteLike::getUserId, currentUserId)
                                    .in(NoteLike::getNoteId, noteIds))
                    .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
            items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
        }

        Long nextCursor = hasMore ? rows.getLast().getCursorId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /** 校验笔记是否存在且已发布。*/
    private void ensureNoteExists(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }
    }

    /**
     * 将笔记实体和图片列表转换为前端响应DTO
     *
     * @param note   笔记实体
     * @param images 笔记关联的图片列表
     * @return 组装好的 NoteResponse 对象
     */
    private NoteResponse toResponse(Note note, List<NoteImage> images) {
        NoteResponse resp = new NoteResponse();
        resp.setId(note.getId());
        resp.setUserId(note.getUserId());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setStatus(note.getStatus());
        resp.setCreateTime(note.getCreateTime());
        resp.setUpdateTime(note.getUpdateTime());
        // 转换图片列表
        resp.setImages(images.stream().map(img -> {
            NoteResponse.ImageItem item = new NoteResponse.ImageItem();
            item.setId(img.getId());
            item.setUrl(img.getUrl());
            item.setSortOrder(img.getSortOrder());
            return item;
        }).toList());
        return resp;
    }
}
