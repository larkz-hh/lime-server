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
import com.lzz.lime_server.dto.response.NoteVideoInfo;
import com.lzz.lime_server.entity.*;
import com.lzz.lime_server.mapper.*;
import com.lzz.lime_server.service.NoteService;
import com.lzz.lime_server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * 笔记接口实现类
 * <p>负责笔记的发布、信息流获取、视频流、点赞、收藏等。
 * 视频笔记（noteType=2）与图文笔记共用 note 主表，视频元数据存 note_video。</p>
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final NoteFavMapper noteFavMapper;
    private final NoteViewMapper noteViewMapper;
    private final NoteVideoMapper noteVideoMapper;
    private final NoteDanmakuMapper noteDanmakuMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;
    private static final String LIKE_COUNT_PREFIX = "note:like:";
    private static final String FAV_COUNT_PREFIX  = "note:fav:";
    private static final long   COUNT_TTL_MINUTES = 10;

    /**
     * 发布笔记（图文 / 视频，按 noteType 区分）。
     * <p>图文：标题/正文至少一项、图片 1~9 张；视频：video 必填、标题/正文可全空。
     * 事务控制确保笔记主体与关联数据（图片或视频元数据）同时成功或同时回滚。</p>
     *
     * @param userId  当前登录用户的ID（从JWT中解析获取）
     * @param request 发布笔记的请求参数
     * @return 发布成功后的笔记响应数据
     * @throws BusinessException 当类型非法或必填项缺失时抛出业务异常
     */
    @Override
    @Transactional
    public NoteResponse publishNote(Long userId, PublishNoteRequest request) {
        Integer noteType = request.getNoteType() != null ? request.getNoteType() : Note.TYPE_TEXT;
        if (noteType != Note.TYPE_TEXT && noteType != Note.TYPE_VIDEO) {
            throw new BusinessException("noteType 参数非法，可选值：1=图文, 2=视频");
        }
        if (noteType == Note.TYPE_VIDEO) {
            return publishVideoNote(userId, request);
        }
        return publishTextNote(userId, request);
    }

    /** 编辑笔记，已发布存草稿建副本，草稿发布覆盖原笔记或就地发布 */
    @Override
    @Transactional
    public NoteResponse updateNote(Long noteId, Long userId, PublishNoteRequest request) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException("无权编辑该笔记");
        }

        boolean isVideo = note.getNoteType() != null && note.getNoteType() == Note.TYPE_VIDEO;
        int newStatus = request.getStatus() != null ? request.getStatus() : 1;
        validateContent(request, isVideo);

        if (note.getStatus() == 1) {
            // 编辑已发布，存草稿新建草稿副本；，接发布就地覆盖
            if (newStatus == 0) {
                return saveAsDraft(note, request, isVideo);
            }
            return updateInPlace(noteId, request, isVideo, 1);
        }

        // 编辑草稿
        if (newStatus == 0) {
            return updateInPlace(noteId, request, isVideo, 0);
        }
        // 发布草稿
        if (note.getSourceNoteId() != null) {
            Note original = noteMapper.selectById(note.getSourceNoteId());
            if (original != null) {
                // 覆盖原笔记，删草稿
                updateInPlace(original.getId(), request, isVideo, 1);
                noteImageMapper.delete(new LambdaQueryWrapper<NoteImage>().eq(NoteImage::getNoteId, noteId));
                if (isVideo) {
                    noteVideoMapper.delete(new LambdaQueryWrapper<NoteVideo>().eq(NoteVideo::getNoteId, noteId));
                }
                noteMapper.deleteById(noteId);
                Note fresh = noteMapper.selectById(original.getId());
                return toResponse(fresh, loadImages(original.getId()), loadVideo(original.getId()));
            }
            updateInPlace(noteId, request, isVideo, 1);
            noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId).set(Note::getSourceNoteId, null));
            return toResponse(noteMapper.selectById(noteId), loadImages(noteId), loadVideo(noteId));
        }
        return updateInPlace(noteId, request, isVideo, 1);
    }

    /** 编辑已发布笔记存草稿，新建草稿副本，源笔记不动 */
    private NoteResponse saveAsDraft(Note source, PublishNoteRequest request, boolean isVideo) {
        Note draft = new Note();
        draft.setUserId(source.getUserId());
        draft.setTitle(request.getTitle());
        draft.setContent(request.getContent());
        draft.setNoteType(source.getNoteType());
        draft.setStatus(0);
        draft.setSourceNoteId(source.getId());
        draft.setLikeCount(0);
        draft.setFavCount(0);
        draft.setViewCount(0);
        draft.setCommentCount(0);
        noteMapper.insert(draft);

        if (isVideo) {
            PublishNoteRequest.VideoItem v = request.getVideo();
            NoteVideo nv = new NoteVideo();
            nv.setNoteId(draft.getId());
            nv.setOriginalUrl(v.getUrl());
            nv.setCoverUrl(v.getCoverUrl());
            nv.setCoverWidth(v.getCoverWidth());
            nv.setCoverHeight(v.getCoverHeight());
            nv.setVideoWidth(v.getWidth());
            nv.setVideoHeight(v.getHeight());
            nv.setDurationMs(v.getDurationMs());
            nv.setTranscodeStatus(2);
            noteVideoMapper.insert(nv);
        } else {
            Map<String, NoteImage> oldByUrl = loadImagesMap(source.getId());
            request.getImages().forEach(item -> {
                NoteImage img = new NoteImage();
                img.setNoteId(draft.getId());
                img.setUrl(item.getUrl());
                NoteImage old = oldByUrl.get(item.getUrl());
                img.setWidth(item.getWidth() != null ? item.getWidth() : (old != null ? old.getWidth() : null));
                img.setHeight(item.getHeight() != null ? item.getHeight() : (old != null ? old.getHeight() : null));
                img.setSortOrder(item.getSortOrder());
                noteImageMapper.insert(img);
            });
        }
        return toResponse(draft, loadImages(draft.getId()), loadVideo(draft.getId()));
    }

    /** 就地更新主表与图片/视频，计数不变 */
    private NoteResponse updateInPlace(Long noteId, PublishNoteRequest request, boolean isVideo, int status) {
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .set(Note::getTitle, request.getTitle())
                .set(Note::getContent, request.getContent())
                .set(Note::getStatus, status)
                .set(Note::getUpdateTime, LocalDateTime.now()));

        if (isVideo) {
            PublishNoteRequest.VideoItem v = request.getVideo();
            NoteVideo nv = new NoteVideo();
            nv.setOriginalUrl(v.getUrl());
            nv.setCoverUrl(v.getCoverUrl());
            nv.setCoverWidth(v.getCoverWidth());
            nv.setCoverHeight(v.getCoverHeight());
            nv.setVideoWidth(v.getWidth());
            nv.setVideoHeight(v.getHeight());
            nv.setDurationMs(v.getDurationMs());
            noteVideoMapper.update(nv, new LambdaQueryWrapper<NoteVideo>().eq(NoteVideo::getNoteId, noteId));
        } else {
            Map<String, NoteImage> oldByUrl = loadImagesMap(noteId);
            noteImageMapper.delete(new LambdaQueryWrapper<NoteImage>().eq(NoteImage::getNoteId, noteId));
            request.getImages().forEach(item -> {
                NoteImage img = new NoteImage();
                img.setNoteId(noteId);
                img.setUrl(item.getUrl());
                NoteImage old = oldByUrl.get(item.getUrl());
                img.setWidth(item.getWidth() != null ? item.getWidth() : (old != null ? old.getWidth() : null));
                img.setHeight(item.getHeight() != null ? item.getHeight() : (old != null ? old.getHeight() : null));
                img.setSortOrder(item.getSortOrder());
                noteImageMapper.insert(img);
            });
        }
        Note fresh = noteMapper.selectById(noteId);
        return toResponse(fresh, loadImages(noteId), loadVideo(noteId));
    }

    /** 校验编辑内容*/
    private void validateContent(PublishNoteRequest request, boolean isVideo) {
        if (isVideo) {
            if (request.getVideo() == null || !StringUtils.hasText(request.getVideo().getUrl())) {
                throw new BusinessException("视频 URL 不能为空");
            }
        } else {
            if (!StringUtils.hasText(request.getTitle()) && !StringUtils.hasText(request.getContent())) {
                throw new BusinessException("标题和正文不能同时为空");
            }
            if (request.getImages() == null || request.getImages().isEmpty()) {
                throw new BusinessException("至少上传一张图片");
            }
        }
    }

    private Map<String, NoteImage> loadImagesMap(Long noteId) {
        return noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>().eq(NoteImage::getNoteId, noteId))
                .stream().collect(Collectors.toMap(NoteImage::getUrl, img -> img, (a, b) -> a));
    }

    private List<NoteImage> loadImages(Long noteId) {
        return noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                .eq(NoteImage::getNoteId, noteId).orderByAsc(NoteImage::getSortOrder));
    }

    private NoteVideo loadVideo(Long noteId) {
        return noteVideoMapper.selectOne(new LambdaQueryWrapper<NoteVideo>().eq(NoteVideo::getNoteId, noteId));
    }

    /** 删除笔记（逻辑删除） */
    @Override
    @Transactional
    public void deleteNote(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该笔记");
        }
        noteMapper.deleteById(noteId);
    }

    /**
     * 发布图文笔记。
     * <p>校验标题/正文至少一项、图片 1~9 张，插入 note 主表与 note_image。</p>
     *
     * @param userId  当前登录用户 ID
     * @param request 发布请求
     * @return 发布成功的笔记响应
     */
    private NoteResponse publishTextNote(Long userId, PublishNoteRequest request) {
        if (!StringUtils.hasText(request.getTitle()) && !StringUtils.hasText(request.getContent())) {
            throw new BusinessException("标题和正文不能同时为空");
        }
        if (request.getImages() == null || request.getImages().isEmpty()) {
            throw new BusinessException("至少上传一张图片");
        }

        // 构建并保存笔记主体信息
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setNoteType(Note.TYPE_TEXT);
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
            img.setWidth(item.getWidth());// 宽高客户端上报
            img.setHeight(item.getHeight());
            img.setSortOrder(item.getSortOrder());
            return img;
        }).toList();
        images.forEach(noteImageMapper::insert);// 逐条插入图片

        return toResponse(note, images, null);
    }

    /**
     * 发布视频笔记。
     * <p>video 必填，标题/正文可全空；创建 note 主表 + note_video 元数据。
     * 直放模式：transcodeStatus 置为 2（可播），播放地址即原片，不触发转码。</p>
     *
     * @param userId  当前登录用户 ID
     * @param request 发布请求
     * @return 发布成功的笔记响应
     */
    private NoteResponse publishVideoNote(Long userId, PublishNoteRequest request) {
        PublishNoteRequest.VideoItem video = request.getVideo();
        if (video == null || !StringUtils.hasText(video.getUrl())) {
            throw new BusinessException("视频 URL 不能为空");
        }

        Note note = new Note();
        note.setUserId(userId);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setNoteType(Note.TYPE_VIDEO);
        note.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        note.setLikeCount(0);
        note.setFavCount(0);
        note.setViewCount(0);
        noteMapper.insert(note);

        NoteVideo noteVideo = new NoteVideo();
        noteVideo.setNoteId(note.getId());
        noteVideo.setOriginalUrl(video.getUrl());
        noteVideo.setCoverUrl(video.getCoverUrl());
        noteVideo.setCoverWidth(video.getCoverWidth());// 封面宽高客户端上报，瀑布流卡片布局用
        noteVideo.setCoverHeight(video.getCoverHeight());
        noteVideo.setVideoWidth(video.getWidth());
        noteVideo.setVideoHeight(video.getHeight());
        noteVideo.setDurationMs(video.getDurationMs());
        noteVideo.setTranscodeStatus(2);// 直放模式：可播
        noteVideoMapper.insert(noteVideo);

        return toResponse(note, List.of(), noteVideo);
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
            item.setCoverWidth(row.getCoverWidth());
            item.setCoverHeight(row.getCoverHeight());
            item.setLikeCount(row.getLikeCount());
            item.setStatus(row.getStatus());
            item.setNoteType(row.getNoteType());
            item.setVideo(toVideoInfo(row));
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

        fillAuthorFollow(items, currentUserId);

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
        List<NoteFeedResponse> items = rows.stream().map(this::toFeedItem).toList();
        fillLiked(items, userId);
        fillAuthorFollow(items, userId);

        // 还有下一页，将当前页最后一条笔记的 ID 作为下一次请求的游标
        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 获取视频流（独立视频 Tab 与详情页上滑共用），游标分页。
     * <p>仅返回视频笔记，按 id 倒序（最新在前）；seedNoteId 用于从指定笔记之后开始取，
     * 保证详情页上滑顺序与进入时的队列一致。每条含 video 信息（playUrl 等），
     * 一次返回多条即天然的「预加载下一条」数据源。</p>
     * <p>orientation 可选过滤横竖屏（推荐流全屏横屏会话用）：
     * LANDSCAPE 只出宽&gt;高的横屏视频，PORTRAIT 只出竖屏，null 不限。</p>
     *
     * @param cursor      上一页最后一条视频笔记的 ID，首次传 null
     * @param seedNoteId  起始锚点笔记 ID（返回 id ≤ 它的视频，含自身），可为 null
     * @param orientation 横竖屏过滤：LANDSCAPE / PORTRAIT / null（不限）
     * @param size        每页条数
     * @param userId      当前登录用户 ID，用于批量填充点赞状态
     * @return 视频卡片分页结果
     */
    @Override
    public CursorPage<NoteFeedResponse> getVideoFeed(Long cursor, Long seedNoteId, String orientation, int size, Long userId) {
        // 多查一条判断是否还有下一页
        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectVideoFeed(cursor, seedNoteId, orientation, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(this::toFeedItem).toList();
        fillLiked(items, userId);
        fillAuthorFollow(items, userId);

        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 关注动态,关注的人发布的笔记，游标分页。
     * <p>作者均为已关注的人，isFollowing 恒为 true，isFollowedBack 标记是否互关。</p>
     *
     * @param userId 当前登录用户 ID
     * @param cursor 上一页最后一条笔记 ID，首次传 null
     * @param size   每页条数
     * @return 笔记卡片分页结果
     */
    @Override
    public CursorPage<NoteFeedResponse> getFollowingFeed(Long userId, Long cursor, int size) {
        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectFollowingFeed(userId, cursor, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(this::toFeedItem).toList();
        fillLiked(items, userId);
        fillAuthorFollow(items, userId);

        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 将数据库投影行转换为信息流卡片响应（feed 与 video-feed 共用）。
     *
     * @param row 查询投影行
     * @return 卡片响应对象（含作者与视频摘要）
     */
    private NoteFeedResponse toFeedItem(NoteMapper.NoteFeedRow row) {
        NoteFeedResponse item = new NoteFeedResponse();
        item.setId(row.getId());
        item.setTitle(row.getTitle());
        item.setCoverImage(row.getCoverImage());
        item.setCoverWidth(row.getCoverWidth());
        item.setCoverHeight(row.getCoverHeight());
        item.setLikeCount(row.getLikeCount());
        item.setNoteType(row.getNoteType());
        item.setVideo(toVideoInfo(row));

        NoteFeedResponse.AuthorBrief author = new NoteFeedResponse.AuthorBrief();
        author.setId(row.getAuthorId());
        author.setNickname(row.getAuthorNickname());
        author.setAvatar(row.getAuthorAvatar());
        item.setAuthor(author);
        return item;
    }

    /**
     * 批量填充当前用户对这批笔记的点赞状态。
     * <p>一次 IN 查询代替 N 次单条查询（与历史实现一致）。</p>
     *
     * @param items  卡片列表
     * @param userId 当前登录用户 ID
     */
    private void fillLiked(List<NoteFeedResponse> items, Long userId) {
        if (items.isEmpty()) return;
        List<Long> noteIds = items.stream().map(NoteFeedResponse::getId).toList();
        Set<Long> likedNoteIds = noteLikeMapper.selectList(
                        new LambdaQueryWrapper<NoteLike>()
                                .eq(NoteLike::getUserId, userId)
                                .in(NoteLike::getNoteId, noteIds))
                .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
        items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
    }

    /**
     * 由投影行组装嵌套视频信息；图文笔记返回 null（序列化时不输出）。
     *
     * @param row 查询投影行
     * @return 视频信息对象，非视频笔记时为 null
     */
    private NoteVideoInfo toVideoInfo(NoteMapper.NoteFeedRow row) {
        if (row.getNoteType() == null || row.getNoteType() != Note.TYPE_VIDEO) return null;
        return NoteVideoInfo.of(row.getVideoDurationMs(), row.getVideoWidth(), row.getVideoHeight(),
                row.getVideoPlayUrl(), null);
    }


    /**
     * 构建笔记详情响应对象。
     *
     * @param note         笔记实体对象，包含标题、内容、状态及时间等基础信息
     * @param images       笔记关联的图片列表
     * @param author       笔记作者的用户信息（允许为 null）
     * @param likeCount    笔记的点赞总数
     * @param favCount     笔记的收藏总数
     * @param liked        当前登录用户是否已点赞该笔记
     * @param favorited    当前登录用户是否已收藏该笔记
     * @param noteVideo    视频元数据（视频笔记才有，图文为 null）
     * @param danmakuCount 弹幕数（视频笔记有意义，图文为 0）
     * @return 组装完成的笔记详情响应对象
     */
    private NoteDetailResponse buildDetailResponse(Note note, List<NoteImage> images, User author,
                                                   int likeCount, int favCount,
                                                   boolean liked, boolean favorited,
                                                   NoteVideo noteVideo, int danmakuCount) {
        NoteDetailResponse resp = new NoteDetailResponse();
        resp.setId(note.getId());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setStatus(note.getStatus());
        resp.setNoteType(note.getNoteType());
        resp.setLikeCount(likeCount);
        resp.setFavCount(favCount);
        resp.setViewCount(note.getViewCount());
        resp.setCommentCount(note.getCommentCount() != null ? note.getCommentCount() : 0);
        resp.setDanmakuCount(danmakuCount);
        resp.setLiked(liked);
        resp.setFavorited(favorited);
        resp.setCreateTime(note.getCreateTime());
        resp.setUpdateTime(note.getUpdateTime());

        if (noteVideo != null) {
            resp.setVideo(NoteVideoInfo.of(noteVideo.getDurationMs(), noteVideo.getVideoWidth(),
                    noteVideo.getVideoHeight(), noteVideo.getOriginalUrl(), noteVideo.getCoverUrl()));
        }

        resp.setImages(images.stream().map(img -> {
            NoteDetailResponse.ImageItem item = new NoteDetailResponse.ImageItem();
            item.setId(img.getId());
            item.setUrl(img.getUrl());
            item.setWidth(img.getWidth());
            item.setHeight(img.getHeight());
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
     * <p>noView=true 时跳过「浏览量 +1 与浏览历史写入」，供视频流补水（hydrate）场景使用，
     * 保证刷视频时滑过的视频不计浏览，只有真正打开详情才算。</p>
     *
     * @param noteId        笔记 ID
     * @param currentUserId 当前登录用户 ID，用于判断 liked / favorited 状态
     * @param noView        true=不累计浏览量、不写浏览历史（默认 false 与历史行为一致）
     * @return 笔记详情响应，包含完整图文、作者信息、互动计数及当前用户状态
     */
    @Override
    public NoteDetailResponse getNoteDetail(Long noteId, Long currentUserId, boolean noView) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getStatus() != 1 && !note.getUserId().equals(currentUserId))) {
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

        // 仅已发布笔记计浏览
        if (note.getStatus() == 1 && !noView) {
            noteMapper.incrementViewCount(noteId);
            noteViewMapper.upsertView(currentUserId, noteId);
        }

        // 视频笔记：加载视频元数据与弹幕数（弹幕独立计数，不计入 commentCount）
        NoteVideo noteVideo = null;
        int danmakuCount = 0;
        if (note.getNoteType() != null && note.getNoteType() == Note.TYPE_VIDEO) {
            noteVideo = noteVideoMapper.selectOne(new LambdaQueryWrapper<NoteVideo>()
                    .eq(NoteVideo::getNoteId, noteId));
            danmakuCount = Math.toIntExact(noteDanmakuMapper.selectCount(
                    new LambdaQueryWrapper<NoteDanmaku>().eq(NoteDanmaku::getNoteId, noteId)));
        }

        NoteDetailResponse resp = buildDetailResponse(note, images, author, likeCount, favCount,
                liked, favorited, noteVideo, danmakuCount);
        fillDetailAuthorFollow(resp, currentUserId);
        return resp;
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
        Note note = ensureNoteExists(noteId);

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

        // 点赞后通知笔记作者
        notificationService.notifyUser(userId, note.getUserId(), Notification.TYPE_LIKE, noteId, null, null);
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
        Note note = ensureNoteExists(noteId);

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

        // 收藏后通知笔记作者
        notificationService.notifyUser(userId, note.getUserId(), Notification.TYPE_FAVORITE, noteId, null, null);
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
        List<NoteMapper.NoteFeedRow> rows = noteMapper.selectViewedNotes(userId, cursor, size + 1);

        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<NoteFeedResponse> items = rows.stream().map(row -> {
            NoteFeedResponse item = new NoteFeedResponse();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverImage(row.getCoverImage());
            item.setCoverWidth(row.getCoverWidth());
            item.setCoverHeight(row.getCoverHeight());
            item.setLikeCount(row.getLikeCount());
            item.setViewTime(row.getViewTime());
            item.setNoteType(row.getNoteType());
            item.setVideo(toVideoInfo(row));
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
                                    .eq(NoteLike::getUserId, userId)
                                    .in(NoteLike::getNoteId, noteIds))
                    .stream().map(NoteLike::getNoteId).collect(Collectors.toSet());
            items.forEach(item -> item.setLiked(likedNoteIds.contains(item.getId())));
        }

        fillAuthorFollow(items, userId);

        Long nextCursor = hasMore ? rows.getLast().getCursorId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /** 从浏览历史中批量删除指定笔记记录（记录不存在时幂等返回）。*/
    @Override
    public void deleteViewRecords(Long userId, List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        noteViewMapper.delete(new LambdaQueryWrapper<NoteView>()
                .eq(NoteView::getUserId, userId)
                .in(NoteView::getNoteId, noteIds));
    }

    /** 清空当前用户的全部浏览历史。*/
    @Override
    public void clearViewHistory(Long userId) {
        noteViewMapper.delete(new LambdaQueryWrapper<NoteView>()
                .eq(NoteView::getUserId, userId));
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
            item.setCoverWidth(row.getCoverWidth());
            item.setCoverHeight(row.getCoverHeight());
            item.setLikeCount(row.getLikeCount());
            item.setNoteType(row.getNoteType());
            item.setVideo(toVideoInfo(row));
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

        fillAuthorFollow(items, currentUserId);

        Long nextCursor = hasMore ? rows.getLast().getCursorId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /** 校验笔记是否存在且已发布，返回笔记实体。*/
    private Note ensureNoteExists(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    /**
     * 批量填充卡片作者的关注状态
     */
    private void fillAuthorFollow(List<NoteFeedResponse> items, Long currentUserId) {
        if (items.isEmpty() || currentUserId == null) return;
        List<Long> authorIds = items.stream()
                .map(NoteFeedResponse::getAuthor)
                .filter(Objects::nonNull)
                .map(NoteFeedResponse.AuthorBrief::getId)
                .distinct().toList();
        if (authorIds.isEmpty()) return;

        // 当前用户关注的作者集合
        Set<Long> followedIds = userFollowMapper.selectList(
                        new LambdaQueryWrapper<UserFollow>()
                                .eq(UserFollow::getFollowerId, currentUserId)
                                .in(UserFollow::getFolloweeId, authorIds))
                .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
        // 关注了当前用户的作者集合
        Set<Long> followedBackIds = userFollowMapper.selectList(
                        new LambdaQueryWrapper<UserFollow>()
                                .eq(UserFollow::getFolloweeId, currentUserId)
                                .in(UserFollow::getFollowerId, authorIds))
                .stream().map(UserFollow::getFollowerId).collect(Collectors.toSet());

        items.forEach(item -> {
            if (item.getAuthor() != null) {
                item.getAuthor().setIsFollowing(followedIds.contains(item.getAuthor().getId()));
                item.getAuthor().setIsFollowedBack(followedBackIds.contains(item.getAuthor().getId()));
            }
        });
    }

    /** 填充笔记详情作者的关注状态（*/
    private void fillDetailAuthorFollow(NoteDetailResponse resp, Long currentUserId) {
        NoteDetailResponse.AuthorInfo author = resp.getAuthor();
        if (author == null || currentUserId == null || currentUserId.equals(author.getId())) return;
        author.setIsFollowing(userFollowMapper.existsFollow(currentUserId, author.getId()) > 0);
        author.setIsFollowedBack(userFollowMapper.existsFollow(author.getId(), currentUserId) > 0);
    }

    /**
     * 将笔记实体、图片列表与视频元数据转换为前端响应DTO
     *
     * @param note      笔记实体
     * @param images    笔记关联的图片列表
     * @param noteVideo 视频元数据（视频笔记才有，图文为 null）
     * @return 组装好的 NoteResponse 对象
     */
    private NoteResponse toResponse(Note note, List<NoteImage> images, NoteVideo noteVideo) {
        NoteResponse resp = new NoteResponse();
        resp.setId(note.getId());
        resp.setUserId(note.getUserId());
        resp.setTitle(note.getTitle());
        resp.setContent(note.getContent());
        resp.setStatus(note.getStatus());
        resp.setNoteType(note.getNoteType());
        resp.setCreateTime(note.getCreateTime());
        resp.setUpdateTime(note.getUpdateTime());
        if (noteVideo != null) {
            resp.setVideo(NoteVideoInfo.of(noteVideo.getDurationMs(), noteVideo.getVideoWidth(),
                    noteVideo.getVideoHeight(), noteVideo.getOriginalUrl(), noteVideo.getCoverUrl()));
        }
        // 转换图片列表
        resp.setImages(images.stream().map(img -> {
            NoteResponse.ImageItem item = new NoteResponse.ImageItem();
            item.setId(img.getId());
            item.setUrl(img.getUrl());
            item.setWidth(img.getWidth());
            item.setHeight(img.getHeight());
            item.setSortOrder(img.getSortOrder());
            return item;
        }).toList());
        return resp;
    }
}
