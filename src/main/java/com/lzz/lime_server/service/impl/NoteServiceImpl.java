package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
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
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private static final String LIKE_COUNT_PREFIX = "note:like:";
    private static final String FAV_COUNT_PREFIX  = "note:fav:";

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
     * 获取笔记信息流数据，游标分页。
     *
     * @param cursor 游标，即上一页最后一条笔记的ID。首次请求时传 null，后续请求传入返回的 nextCursor
     * @param size   每页期望获取的笔记条数
     * @return       包含笔记列表、下一页游标及是否有更多数据的分页对象
     */
    @Override
    public CursorPage<NoteFeedResponse> getFeed(Long cursor, int size) {
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

        // 还有下一页，将当前页最后一条笔记的 ID 作为下一次请求的游标
        Long nextCursor = hasMore ? items.getLast().getId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
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
