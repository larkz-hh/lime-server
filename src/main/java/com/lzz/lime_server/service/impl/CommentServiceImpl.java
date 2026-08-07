package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PublishCommentRequest;
import com.lzz.lime_server.dto.response.CommentResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.ReplyResponse;
import com.lzz.lime_server.entity.*;
import com.lzz.lime_server.mapper.*;
import com.lzz.lime_server.service.CommentService;
import com.lzz.lime_server.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final NoteCommentMapper      commentMapper;
    private final NoteCommentImageMapper commentImageMapper;
    private final CommentLikeMapper      commentLikeMapper;
    private final NoteMapper             noteMapper;
    private final UserMapper             userMapper;
    private final IpLocationService      ipLocationService;
    private final StringRedisTemplate    redisTemplate;
    private final ObjectMapper           objectMapper;

    // 热度排序第一页缓存 key 前缀，TTL 3 分钟
    private static final String HOT_FIRST_PAGE_PREFIX = "comment:hot1st:";

    // 一级评论每次查询携带的回复预览条数（UI 未展开时显示 1 条）
    private static final int TOP_REPLY_SIZE = 1;

    /**
     * 发布一级评论。
     * <p>内容约束：content/images/voiceUrl 三者至少传一个；images 与 voiceUrl 互斥。</p>
     *
     * @param noteId    笔记 ID
     * @param userId    当前登录用户 ID
     * @param request   评论内容（文字/图片/语音）
     * @param ipAddress 客户端真实 IP，存入 ip_address 字段
     * @return 发布成功后的评论响应数据
     * @throws BusinessException 笔记不存在、内容为空或图片与语音同时存在时抛出
     */
    @Override
    @Transactional
    public CommentResponse publishComment(Long noteId, Long userId,
                                          PublishCommentRequest request, String ipAddress) {
        Note note = ensureNoteExists(noteId);
        validateContent(request);

        NoteComment comment = buildComment(noteId, userId, null, null, request, ipAddress);
        comment.setIpLocation(ipLocationService.resolve(ipAddress));
        commentMapper.insert(comment);
        saveImages(comment.getId(), request.getImages());

        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("comment_count = comment_count + 1"));

        // 发布新评论后使该笔记的热度缓存失效
        evictHotCache(noteId);

        CommentResponse resp = toCommentResponse(comment, note.getUserId(), Collections.emptyList(), false);
        fillCommentAuthor(resp, userId);
        return resp;
    }

    /**
     * 发布回复（二级评论）。
     * <p>内容约束同一级评论；同时校验父评论必须属于该笔记的一级评论（不允许三级嵌套）。</p>
     *
     * @param noteId      笔记 ID
     * @param parentId    被回复的一级评论 ID
     * @param userId      当前登录用户 ID
     * @param request     回复内容；{@code replyToUserId} 为被回复用户 ID，用于显示"回复@xxx"
     * @param ipAddress   客户端真实 IP
     * @return 发布成功后的回复响应数据
     * @throws BusinessException 笔记/父评论不存在、内容非法时抛出
     */
    @Override
    @Transactional
    public ReplyResponse publishReply(Long noteId, Long parentId, Long userId,
                                      PublishCommentRequest request, String ipAddress) {
        Note note = ensureNoteExists(noteId);
        validateContent(request);

        // 校验父评论存在且属于该笔记的一级评论
        NoteComment parent = commentMapper.selectById(parentId);
        if (parent == null || parent.getDeleted() == 1
                || !parent.getNoteId().equals(noteId) || parent.getParentId() != null) {
            throw new BusinessException("评论不存在");
        }

        NoteComment reply = buildComment(noteId, userId, parentId,
                request.getReplyToUserId(), request, ipAddress);
        reply.setIpLocation(ipLocationService.resolve(ipAddress));
        commentMapper.insert(reply);
        saveImages(reply.getId(), request.getImages());

        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .setSql("comment_count = comment_count + 1"));

        // 更新父评论的 reply_count 和 hot_score
        commentMapper.update(null, new LambdaUpdateWrapper<NoteComment>()
                .eq(NoteComment::getId, parentId)
                .setSql("reply_count = reply_count + 1, hot_score = like_count + (reply_count + 1) * 2"));

        evictHotCache(noteId);

        ReplyResponse resp = toReplyResponse(reply, note.getUserId(), false, null);
        fillReplyAuthorFromUser(resp, userId);
        return resp;
    }


    /**
     * 获取笔记的一级评论列表，游标分页。
     *
     * @param noteId        笔记 ID
     * @param sort          排序方式：{@code hot}=热度降序，{@code time}=最新在前
     * @param cursor        分页游标；热度排序格式 {@code "{hotScore}:{id}"}，时间排序格式 {@code "{id}"}；首次传 null
     * @param size          每页条数
     * @param currentUserId 当前登录用户 ID，用于标记点赞状态
     * @return 评论分页数据，每条一级评论附带前 {@value #TOP_REPLY_SIZE} 条回复预览
     */
    @Override
    public CursorPage<CommentResponse> getComments(Long noteId, String sort, String cursor,
                                                   int size, Long currentUserId) {
        Note note = ensureNoteExists(noteId);
        boolean isHot = "hot".equals(sort);// 判断当前请求的是热度排序还是时间排序

        if (isHot) {
            if (cursor == null) {
                // 热度第一页：优先读缓存（缓存内容不含点赞状态，命中后从 DB 补充）
                String cacheKey = HOT_FIRST_PAGE_PREFIX + noteId;
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    try {
                        CursorPage<CommentResponse> page = objectMapper.readValue(
                                cached, new TypeReference<CursorPage<CommentResponse>>() {});
                        supplementLiked(page, currentUserId);
                        return page;
                    } catch (Exception ignored) {
                        // 缓存数据损坏，降级走 DB
                    }
                }

                // 缓存未命中，查 DB 并写入缓存
                List<NoteCommentMapper.CommentRow> rows = commentMapper.selectByHot(noteId, null, null, size + 1);
                boolean hasMore = rows.size() > size;
                if (hasMore) rows = rows.subList(0, size);
                NoteCommentMapper.CommentRow last = hasMore ? rows.getLast() : null;
                String nextCursor = (last != null) ? last.getHotScore() + ":" + last.getId() : null;

                // 构建不含点赞状态的基础页写入缓存，避免缓存用户个性化数据
                CursorPage<CommentResponse> page = buildCommentPageBase(rows, nextCursor, hasMore, note.getUserId());
                try {
                    redisTemplate.opsForValue().set(
                            cacheKey, objectMapper.writeValueAsString(page), Duration.ofMinutes(3));
                } catch (Exception ignored) {}

                supplementLiked(page, currentUserId);
                return page;
            }

            // 热度后续页（cursor != null），直接走 DB
            String[] parts = cursor.split(":");
            Integer cursorScore = Integer.parseInt(parts[0]);
            Long cursorId = Long.parseLong(parts[1]);
            List<NoteCommentMapper.CommentRow> rows = commentMapper.selectByHot(noteId, cursorScore, cursorId, size + 1);
            boolean hasMore = rows.size() > size;
            if (hasMore) rows = rows.subList(0, size);
            NoteCommentMapper.CommentRow last = hasMore ? rows.getLast() : null;
            String nextCursor = (last != null) ? last.getHotScore() + ":" + last.getId() : null;
            return buildCommentPage(rows, nextCursor, hasMore, note.getUserId(), currentUserId);
        }

        // 时间排序，cursor 就是 id，直接走 DB 保证实时性
        Long idCursor = (cursor != null) ? Long.parseLong(cursor) : null;
        List<NoteCommentMapper.CommentRow> rows = commentMapper.selectByTime(noteId, idCursor, size + 1);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);
        Long lastId = (hasMore && !rows.isEmpty()) ? rows.getLast().getId() : null;
        String nextCursor = (lastId != null) ? String.valueOf(lastId) : null;
        return buildCommentPage(rows, nextCursor, hasMore, note.getUserId(), currentUserId);
    }

    /**
     * 获取某条一级评论的回复列表，时间正序（最早在顶部，最新在底部），游标分页。
     *
     * @param commentId     一级评论 ID
     * @param cursor        游标（上一页最后一条回复的 id），首次传 null
     * @param size          每页条数
     * @param currentUserId 当前登录用户 ID，用于标记点赞状态
     */
    @Override
    public CursorPage<ReplyResponse> getReplies(Long commentId, Long cursor,
                                                int size, Long currentUserId) {
        NoteComment parent = commentMapper.selectById(commentId);
        if (parent == null || parent.getDeleted() == 1 || parent.getParentId() != null) {
            throw new BusinessException("评论不存在");
        }
        Note note = noteMapper.selectById(parent.getNoteId());
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }

        List<NoteCommentMapper.CommentRow> rows = commentMapper.selectReplies(commentId, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        Long nextCursor = (hasMore && !rows.isEmpty()) ? rows.getLast().getId() : null;

        // 批量查当前用户点赞状态
        Set<Long> likedIds = batchLikedCommentIds(
                rows.stream().map(NoteCommentMapper.CommentRow::getId).toList(), currentUserId);

        List<ReplyResponse> items = rows.stream().map(row -> {
            ReplyResponse resp = toReplyResponse(
                    rowToEntity(row), note.getUserId(), likedIds.contains(row.getId()), row.getReplyToNickname());
            fillReplyAuthor(resp, row);
            return resp;
        }).toList();

        return CursorPage.of(items, nextCursor, hasMore);
    }


    /**
     * 点赞评论或回复，幂等操作（已点赞则直接返回）。
     * <p>点赞后同步更新 {@code like_count} 与 {@code hot_score}，并使热度缓存失效。</p>
     *
     * @param commentId 评论或回复 ID
     * @param userId    当前用户 ID
     */
    @Override
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        NoteComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new BusinessException("评论不存在");
        }

        boolean alreadyLiked = commentLikeMapper.selectCount(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId)) > 0;
        if (alreadyLiked) return;

        CommentLike like = new CommentLike();
        like.setCommentId(commentId);
        like.setUserId(userId);
        commentLikeMapper.insert(like);

        commentMapper.update(null, new LambdaUpdateWrapper<NoteComment>()
                .eq(NoteComment::getId, commentId)
                .setSql("like_count = like_count + 1, hot_score = (like_count + 1) + reply_count * 2"));

        evictHotCache(comment.getNoteId());
    }

    /**
     * 取消点赞，幂等操作（未点赞则直接返回）。
     *
     * @param commentId 评论或回复 ID
     * @param userId    当前用户 ID
     */
    @Override
    @Transactional
    public void unlikeComment(Long commentId, Long userId) {
        NoteComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new BusinessException("评论不存在");
        }

        int deleted = commentLikeMapper.delete(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, userId));
        if (deleted == 0) return;

        commentMapper.update(null, new LambdaUpdateWrapper<NoteComment>()
                .eq(NoteComment::getId, commentId)
                .setSql("like_count = GREATEST(like_count - 1, 0), " +
                        "hot_score = GREATEST(like_count - 1, 0) + reply_count * 2"));

        evictHotCache(comment.getNoteId());
    }

    /**
     * 删除评论或回复（逻辑删除）。
     * <p>
     * 权限：评论者本人或笔记作者均可删除。<br>
     * 若删除的是回复，同步将父评论的 {@code reply_count} 和 {@code hot_score} 减一。
     * </p>
     *
     * @param commentId 评论或回复 ID
     * @param userId    当前用户 ID
     * @throws BusinessException 无权删除时抛出
     */
    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        NoteComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new BusinessException("评论不存在");
        }

        Note note = noteMapper.selectById(comment.getNoteId());
        boolean isCommentOwner = comment.getUserId().equals(userId);
        boolean isNoteOwner    = (note != null && note.getUserId().equals(userId));
        // 判断是笔记作者还是评论作者
        if (!isCommentOwner && !isNoteOwner) {
            throw new BusinessException("无权删除该评论");
        }

        // 逻辑删除（MyBatis-Plus @TableLogic 会自动处理 deleted 字段）
        commentMapper.deleteById(commentId);

        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, comment.getNoteId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));

        // 若删除的是回复，则父评论 reply_count - 1，同步更新 hot_score
        if (comment.getParentId() != null) {
            commentMapper.update(null, new LambdaUpdateWrapper<NoteComment>()
                    .eq(NoteComment::getId, comment.getParentId())
                    .setSql("reply_count = GREATEST(reply_count - 1, 0), " +
                            "hot_score = like_count + GREATEST(reply_count - 1, 0) * 2"));
        }

        evictHotCache(comment.getNoteId());
    }

    /**
     * 校验笔记存在且已发布，返回笔记实体。
     *
     * @throws BusinessException 笔记不存在或未发布时抛出
     */
    private Note ensureNoteExists(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    /**
     * 校验评论内容：至少有一项内容；图片与语音互斥；图片最多 9 张。
     */
    private void validateContent(PublishCommentRequest request) {
        boolean hasContent = StringUtils.hasText(request.getContent());
        boolean hasImages  = (request.getImages() != null && !request.getImages().isEmpty());
        boolean hasVoice   = StringUtils.hasText(request.getVoiceUrl());

        if (!hasContent && !hasImages && !hasVoice) {
            throw new BusinessException("评论内容不能为空");
        }
        if (hasImages && hasVoice) {
            throw new BusinessException("图片和语音不能同时存在");
        }
        if (hasImages && request.getImages().size() > 9) {
            throw new BusinessException("评论最多上传 9 张图片");
        }
        if (hasVoice && request.getVoiceDuration() == null) {
            throw new BusinessException("发送语音时需传入时长");
        }
    }

    /**
     * 构建评论实体（一级评论和回复复用）。
     *
     * @param parentId      一级评论 ID，null 表示本次是一级评论
     * @param replyToUserId 被回复用户 ID，仅二级回复时有值
     */
    private NoteComment buildComment(Long noteId, Long userId, Long parentId,
                                     Long replyToUserId, PublishCommentRequest req,
                                     String ipAddress) {
        NoteComment c = new NoteComment();
        c.setNoteId(noteId);
        c.setUserId(userId);
        c.setParentId(parentId);
        c.setReplyToUserId(replyToUserId);
        c.setContent(req.getContent());
        c.setVoiceUrl(req.getVoiceUrl());
        c.setVoiceDuration(req.getVoiceDuration());
        c.setLikeCount(0);
        c.setReplyCount(0);
        c.setHotScore(0);
        c.setIpAddress(ipAddress);
        return c;
    }

    /**
     * 批量保存评论图片，按传入顺序设置 {@code sort_order}。
     */
    private void saveImages(Long commentId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            NoteCommentImage img = new NoteCommentImage();
            img.setCommentId(commentId);
            img.setUrl(imageUrls.get(i));
            img.setSortOrder(i);
            commentImageMapper.insert(img);
        }
    }

    /** 批量查询当前用户对一批评论的点赞状态，返回已点赞的评论 ID 集合 */
    private Set<Long> batchLikedCommentIds(List<Long> commentIds, Long userId) {
        if (commentIds.isEmpty() || userId == null) return Collections.emptySet();
        return commentLikeMapper.selectList(
                        new LambdaQueryWrapper<CommentLike>()
                                .eq(CommentLike::getUserId, userId)
                                .in(CommentLike::getCommentId, commentIds))
                .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
    }

    /** 批量查询一批评论各自的图片列表，返回 commentId -> urls 的 Map */
    private Map<Long, List<String>> batchImages(List<Long> commentIds) {
        if (commentIds.isEmpty()) return Collections.emptyMap();
        return commentImageMapper.selectList(
                        new LambdaQueryWrapper<NoteCommentImage>()
                                .in(NoteCommentImage::getCommentId, commentIds)
                                .orderByAsc(NoteCommentImage::getSortOrder))
                .stream().collect(Collectors.groupingBy(
                        NoteCommentImage::getCommentId,
                        Collectors.mapping(NoteCommentImage::getUrl, Collectors.toList())));
    }

    /**
     * 将一级评论投影列表组装为分页结果，每条一级评论附带前 {@value #TOP_REPLY_SIZE} 条回复预览。
     * <p>批量查询点赞状态和图片，避免 N+1 查询；回复预览逐条查（每页评论数有限）。</p>
     */
    private CursorPage<CommentResponse> buildCommentPage(
            List<NoteCommentMapper.CommentRow> rows, String nextCursor, boolean hasMore,
            Long noteAuthorId, Long currentUserId) {

        if (rows.isEmpty()) {
            return CursorPage.of(Collections.emptyList(), (String) null, false);
        }

        List<Long> commentIds = rows.stream().map(NoteCommentMapper.CommentRow::getId).toList();

        // 批量查当前用户点赞状态
        Set<Long> likedIds = batchLikedCommentIds(commentIds, currentUserId);

        // 批量查评论图片
        Map<Long, List<String>> imagesMap = batchImages(commentIds);

        // 批量查每条评论的前 3 条回复预览
        // 逐条查（评论数量通常较少，10~20条/页，可以接受）
        List<CommentResponse> items = rows.stream().map(row -> {
            CommentResponse resp = new CommentResponse();
            resp.setId(row.getId());
            resp.setContent(row.getContent());
            resp.setVoiceUrl(row.getVoiceUrl());
            resp.setVoiceDuration(row.getVoiceDuration());
            resp.setLikeCount(row.getLikeCount());
            resp.setReplyCount(row.getReplyCount());
            resp.setLiked(likedIds.contains(row.getId()));
            resp.setNoteAuthor(noteAuthorId.equals(row.getUserId()));
            resp.setCreateTime(row.getCreateTime());
            resp.setIpLocation(row.getIpLocation());
            resp.setImages(imagesMap.getOrDefault(row.getId(), null));

            CommentResponse.AuthorInfo author = new CommentResponse.AuthorInfo();
            author.setId(row.getUserId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            resp.setAuthor(author);

            // 回复预览
            List<NoteCommentMapper.CommentRow> replyRows = commentMapper.selectTopReplies(row.getId(), TOP_REPLY_SIZE);
            if (!replyRows.isEmpty()) {
                List<Long> replyIds = replyRows.stream().map(NoteCommentMapper.CommentRow::getId).toList();
                Set<Long> replyLikedIds = batchLikedCommentIds(replyIds, currentUserId);
                Map<Long, List<String>> replyImagesMap = batchImages(replyIds);

                resp.setTopReplies(replyRows.stream().map(rr -> {
                    ReplyResponse reply = new ReplyResponse();
                    reply.setId(rr.getId());
                    reply.setReplyToUserId(rr.getReplyToUserId());
                    reply.setReplyToNickname(rr.getReplyToNickname());
                    reply.setContent(rr.getContent());
                    reply.setVoiceUrl(rr.getVoiceUrl());
                    reply.setVoiceDuration(rr.getVoiceDuration());
                    reply.setLikeCount(rr.getLikeCount());
                    reply.setLiked(replyLikedIds.contains(rr.getId()));
                    reply.setNoteAuthor(noteAuthorId.equals(rr.getUserId()));
                    reply.setCreateTime(rr.getCreateTime());
                    reply.setIpLocation(rr.getIpLocation());
                    reply.setImages(replyImagesMap.getOrDefault(rr.getId(), null));
                    ReplyResponse.AuthorInfo ra = new ReplyResponse.AuthorInfo();
                    ra.setId(rr.getUserId());
                    ra.setNickname(rr.getAuthorNickname());
                    ra.setAvatar(rr.getAuthorAvatar());
                    reply.setAuthor(ra);
                    return reply;
                }).toList());
            }
            return resp;
        }).toList();

        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 构建不含点赞状态的基础评论页，用于写入 Redis 缓存。
     * <p>所有 {@code liked} 字段均为 {@code false}，调用方须在返回前调用
     * {@link #supplementLiked} 补充当前用户的点赞状态。</p>
     */
    private CursorPage<CommentResponse> buildCommentPageBase(
            List<NoteCommentMapper.CommentRow> rows, String nextCursor, boolean hasMore, Long noteAuthorId) {

        if (rows.isEmpty()) {
            return CursorPage.of(Collections.emptyList(), (String) null, false);
        }

        List<Long> commentIds = rows.stream().map(NoteCommentMapper.CommentRow::getId).toList();
        Map<Long, List<String>> imagesMap = batchImages(commentIds);

        List<CommentResponse> items = rows.stream().map(row -> {
            CommentResponse resp = new CommentResponse();
            resp.setId(row.getId());
            resp.setContent(row.getContent());
            resp.setVoiceUrl(row.getVoiceUrl());
            resp.setVoiceDuration(row.getVoiceDuration());
            resp.setLikeCount(row.getLikeCount());
            resp.setReplyCount(row.getReplyCount());
            resp.setLiked(false);
            resp.setNoteAuthor(noteAuthorId.equals(row.getUserId()));
            resp.setCreateTime(row.getCreateTime());
            resp.setIpLocation(row.getIpLocation());
            resp.setImages(imagesMap.getOrDefault(row.getId(), null));

            CommentResponse.AuthorInfo author = new CommentResponse.AuthorInfo();
            author.setId(row.getUserId());
            author.setNickname(row.getAuthorNickname());
            author.setAvatar(row.getAuthorAvatar());
            resp.setAuthor(author);

            List<NoteCommentMapper.CommentRow> replyRows = commentMapper.selectTopReplies(row.getId(), TOP_REPLY_SIZE);
            if (!replyRows.isEmpty()) {
                List<Long> replyIds = replyRows.stream().map(NoteCommentMapper.CommentRow::getId).toList();
                Map<Long, List<String>> replyImagesMap = batchImages(replyIds);
                resp.setTopReplies(replyRows.stream().map(rr -> {
                    ReplyResponse reply = new ReplyResponse();
                    reply.setId(rr.getId());
                    reply.setReplyToUserId(rr.getReplyToUserId());
                    reply.setReplyToNickname(rr.getReplyToNickname());
                    reply.setContent(rr.getContent());
                    reply.setVoiceUrl(rr.getVoiceUrl());
                    reply.setVoiceDuration(rr.getVoiceDuration());
                    reply.setLikeCount(rr.getLikeCount());
                    reply.setLiked(false);
                    reply.setNoteAuthor(noteAuthorId.equals(rr.getUserId()));
                    reply.setCreateTime(rr.getCreateTime());
                    reply.setIpLocation(rr.getIpLocation());
                    reply.setImages(replyImagesMap.getOrDefault(rr.getId(), null));
                    ReplyResponse.AuthorInfo ra = new ReplyResponse.AuthorInfo();
                    ra.setId(rr.getUserId());
                    ra.setNickname(rr.getAuthorNickname());
                    ra.setAvatar(rr.getAuthorAvatar());
                    reply.setAuthor(ra);
                    return reply;
                }).toList());
            }
            return resp;
        }).toList();

        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 批量查询当前用户对页内所有评论及回复预览的点赞状态，并原地填充 {@code liked} 字段。
     * <p>用于缓存命中和缓存未命中两条路径，统一在返回前执行一次。</p>
     */
    private void supplementLiked(CursorPage<CommentResponse> page, Long currentUserId) {
        if (page.getItems() == null || page.getItems().isEmpty()) return;

        List<Long> allIds = new ArrayList<>();
        for (CommentResponse item : page.getItems()) {
            allIds.add(item.getId());
            if (item.getTopReplies() != null) {
                for (ReplyResponse r : item.getTopReplies()) {
                    allIds.add(r.getId());
                }
            }
        }

        Set<Long> likedIds = batchLikedCommentIds(allIds, currentUserId);

        for (CommentResponse item : page.getItems()) {
            item.setLiked(likedIds.contains(item.getId()));
            if (item.getTopReplies() != null) {
                for (ReplyResponse r : item.getTopReplies()) {
                    r.setLiked(likedIds.contains(r.getId()));
                }
            }
        }
    }

    /**
     * 将评论实体转换为一级评论响应 DTO（用于发布后立即返回，不含作者信息）。
     */
    private CommentResponse toCommentResponse(NoteComment comment, Long noteAuthorId,
                                              List<ReplyResponse> topReplies, boolean liked) {
        CommentResponse resp = new CommentResponse();
        resp.setId(comment.getId());
        resp.setContent(comment.getContent());
        resp.setVoiceUrl(comment.getVoiceUrl());
        resp.setVoiceDuration(comment.getVoiceDuration());
        resp.setLikeCount(comment.getLikeCount());
        resp.setReplyCount(comment.getReplyCount());
        resp.setLiked(liked);
        resp.setNoteAuthor(noteAuthorId.equals(comment.getUserId()));
        resp.setCreateTime(comment.getCreateTime());
        resp.setIpLocation(comment.getIpLocation());
        resp.setTopReplies(topReplies.isEmpty() ? null : topReplies);
        return resp;
    }

    /**
     * 将回复实体转换为回复响应 DTO（用于发布后立即返回，不含作者信息）。
     */
    private ReplyResponse toReplyResponse(NoteComment reply, Long noteAuthorId,
                                          boolean liked, String replyToNickname) {
        ReplyResponse resp = new ReplyResponse();
        resp.setId(reply.getId());
        resp.setReplyToUserId(reply.getReplyToUserId());
        resp.setReplyToNickname(replyToNickname);
        resp.setContent(reply.getContent());
        resp.setVoiceUrl(reply.getVoiceUrl());
        resp.setVoiceDuration(reply.getVoiceDuration());
        resp.setLikeCount(reply.getLikeCount());
        resp.setLiked(liked);
        resp.setNoteAuthor(noteAuthorId.equals(reply.getUserId()));
        resp.setCreateTime(reply.getCreateTime());
        resp.setIpLocation(reply.getIpLocation());
        return resp;
    }

    /**
     * 将 CommentRow 查询投影转换为评论实体，仅填充 {@link #toReplyResponse} 所需字段。
     */
    private NoteComment rowToEntity(NoteCommentMapper.CommentRow row) {
        NoteComment c = new NoteComment();
        c.setId(row.getId());
        c.setNoteId(row.getNoteId());
        c.setUserId(row.getUserId());
        c.setParentId(row.getParentId());
        c.setReplyToUserId(row.getReplyToUserId());
        c.setContent(row.getContent());
        c.setVoiceUrl(row.getVoiceUrl());
        c.setVoiceDuration(row.getVoiceDuration());
        c.setLikeCount(row.getLikeCount());
        c.setCreateTime(row.getCreateTime());
        c.setIpLocation(row.getIpLocation());
        return c;
    }

    /**
     * 将查询投影中的作者字段填充到回复响应的 author 字段。
     */
    private void fillReplyAuthor(ReplyResponse resp, NoteCommentMapper.CommentRow row) {
        ReplyResponse.AuthorInfo author = new ReplyResponse.AuthorInfo();
        author.setId(row.getUserId());
        author.setNickname(row.getAuthorNickname());
        author.setAvatar(row.getAuthorAvatar());
        resp.setAuthor(author);
    }

    /**
     * 从数据库查询用户信息并填充到评论响应的 author 字段。
     */
    private void fillCommentAuthor(CommentResponse resp, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        CommentResponse.AuthorInfo author = new CommentResponse.AuthorInfo();
        author.setId(user.getId());
        author.setNickname(user.getNickname());
        author.setAvatar(user.getAvatar());
        resp.setAuthor(author);
    }

    /**
     * 从数据库查询用户信息并填充到回复响应的 author 字段。
     */
    private void fillReplyAuthorFromUser(ReplyResponse resp, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        ReplyResponse.AuthorInfo author = new ReplyResponse.AuthorInfo();
        author.setId(user.getId());
        author.setNickname(user.getNickname());
        author.setAvatar(user.getAvatar());
        resp.setAuthor(author);
    }

    /**
     * 删除该笔记的热度排序缓存，写操作后调用以保证数据一致性。
     */
    private void evictHotCache(Long noteId) {
        redisTemplate.delete(HOT_FIRST_PAGE_PREFIX + noteId);
    }
}
