package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PostDanmakuRequest;
import com.lzz.lime_server.dto.response.DanmakuListResponse;
import com.lzz.lime_server.dto.response.DanmakuResponse;
import com.lzz.lime_server.entity.Note;
import com.lzz.lime_server.entity.NoteDanmaku;
import com.lzz.lime_server.entity.NoteVideo;
import com.lzz.lime_server.entity.User;
import com.lzz.lime_server.mapper.NoteDanmakuMapper;
import com.lzz.lime_server.mapper.NoteMapper;
import com.lzz.lime_server.mapper.NoteVideoMapper;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.DanmakuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 弹幕服务实现类
 * <p>弹幕 = 带时间轴的轻量评论，仅视频笔记支持，独立于评论区 note_comment。
 * 发送者本人与视频笔记作者均可删除；弹幕数不计入笔记评论数。</p>
 */
@Service
@RequiredArgsConstructor
public class DanmakuServiceImpl implements DanmakuService {

    private final NoteDanmakuMapper danmakuMapper;
    private final NoteMapper noteMapper;
    private final NoteVideoMapper noteVideoMapper;
    private final UserMapper userMapper;

    /** 单视频弹幕返回上限（本地场景保护，超出按时间点截断） */
    private static final int MAX_FETCH = 2000;

    /**
     * 发布弹幕。
     * <p>校验笔记为已发布视频笔记、时间点落在视频时长范围内（容错 +1s）后入库。</p>
     *
     * @param noteId  视频笔记 ID
     * @param userId  当前登录用户 ID
     * @param request 弹幕内容与时间点
     * @return 入库后的弹幕对象（含作者信息）
     */
    @Override
    public DanmakuResponse postDanmaku(Long noteId, Long userId, PostDanmakuRequest request) {
        NoteVideo video = requireVideoNote(noteId);
        if (request.getVideoTimeMs() > video.getDurationMs() + 1000) {
            throw new BusinessException("弹幕时间点超出视频时长范围");
        }

        NoteDanmaku danmaku = new NoteDanmaku();
        danmaku.setNoteId(noteId);
        danmaku.setUserId(userId);
        danmaku.setContent(request.getContent().trim());
        danmaku.setVideoTimeMs(request.getVideoTimeMs());
        danmaku.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : null);
        danmakuMapper.insert(danmaku);

        return toResponse(danmaku, userMapper.selectById(userId));
    }

    /**
     * 拉取视频笔记的弹幕列表。
     * <p>按出现时间点升序返回（同时间点按 id 升序），MVP 全量拉取，
     * 单视频上限 {@value #MAX_FETCH} 条；量大后再改分段拉取。</p>
     *
     * @param noteId 视频笔记 ID
     * @return 弹幕列表（含作者信息）
     */
    @Override
    public DanmakuListResponse listDanmaku(Long noteId) {
        requireVideoNote(noteId);
        List<NoteDanmaku> list = danmakuMapper.selectList(
                new LambdaQueryWrapper<NoteDanmaku>()
                        .eq(NoteDanmaku::getNoteId, noteId)
                        .orderByAsc(NoteDanmaku::getVideoTimeMs)
                        .orderByAsc(NoteDanmaku::getId)
                        .last("LIMIT " + MAX_FETCH));

        // 批量查作者，避免逐条查询
        List<Long> userIds = list.stream().map(NoteDanmaku::getUserId).distinct().toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds)).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<DanmakuResponse> items = list.stream()
                .map(d -> toResponse(d, userMap.get(d.getUserId())))
                .toList();
        return DanmakuListResponse.of(items);
    }

    /**
     * 删除弹幕（逻辑删除）。
     * <p>仅弹幕发送者本人或视频笔记作者可删，否则抛业务异常。</p>
     *
     * @param noteId        视频笔记 ID
     * @param danmakuId     弹幕 ID
     * @param currentUserId 当前登录用户 ID
     */
    @Override
    public void deleteDanmaku(Long noteId, Long danmakuId, Long currentUserId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }
        NoteDanmaku danmaku = danmakuMapper.selectById(danmakuId);
        if (danmaku == null || !danmaku.getNoteId().equals(noteId)) {
            throw new BusinessException("弹幕不存在");
        }
        if (!danmaku.getUserId().equals(currentUserId) && !note.getUserId().equals(currentUserId)) {
            throw new BusinessException("无权删除该弹幕");
        }
        danmakuMapper.deleteById(danmakuId);
    }

    /**
     * 校验笔记存在且为视频笔记，返回其视频元数据。
     *
     * @param noteId 笔记 ID
     * @return 视频元数据
     * @throws BusinessException 笔记不存在、非视频笔记或视频信息缺失时抛出
     */
    private NoteVideo requireVideoNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在");
        }
        if (note.getNoteType() == null || note.getNoteType() != Note.TYPE_VIDEO) {
            throw new BusinessException("仅视频笔记支持弹幕");
        }
        NoteVideo video = noteVideoMapper.selectOne(
                new LambdaQueryWrapper<NoteVideo>().eq(NoteVideo::getNoteId, noteId));
        if (video == null) {
            throw new BusinessException("视频信息不存在");
        }
        return video;
    }

    /**
     * 组装弹幕响应对象。
     *
     * @param danmaku 弹幕实体
     * @param author  弹幕作者（允许为 null，已注销用户降级处理）
     * @return 弹幕响应对象
     */
    private DanmakuResponse toResponse(NoteDanmaku danmaku, User author) {
        DanmakuResponse resp = new DanmakuResponse();
        resp.setId(danmaku.getId());
        resp.setContent(danmaku.getContent());
        resp.setVideoTimeMs(danmaku.getVideoTimeMs());
        resp.setColor(danmaku.getColor());
        resp.setCreateTime(danmaku.getCreateTime());
        if (author != null) {
            DanmakuResponse.AuthorBrief brief = new DanmakuResponse.AuthorBrief();
            brief.setId(author.getId());
            brief.setNickname(author.getNickname());
            brief.setAvatar(author.getAvatar());
            resp.setAuthor(brief);
        }
        return resp;
    }
}
