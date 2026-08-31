package com.lzz.lime_server.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.entity.Note;
import com.lzz.lime_server.entity.NoteComment;
import com.lzz.lime_server.entity.NoteImage;
import com.lzz.lime_server.entity.NoteVideo;
import com.lzz.lime_server.mapper.NoteCommentMapper;
import com.lzz.lime_server.mapper.NoteImageMapper;
import com.lzz.lime_server.mapper.NoteMapper;
import com.lzz.lime_server.mapper.NoteVideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记上下文检索：引用笔记提问时，拉取笔记的标题、正文、图片/视频封面、
 * 互动数据与精选评论，组装成 {@link NoteSnapshot} 喂给模型。
 * 正文/评论/图片均有截断，控制 token 成本。
 */
@Component
@RequiredArgsConstructor
public class NoteContextLoader {

    /** 正文最大字符数 */
    private static final int MAX_CONTENT_CHARS = 2000;

    /** 精选评论最大条数 */
    private static final int MAX_COMMENTS = 20;

    /** 图片最大张数 */
    private static final int MAX_IMAGES = 4;

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final NoteVideoMapper noteVideoMapper;
    private final NoteCommentMapper noteCommentMapper;

    /**
     * 加载笔记上下文快照。笔记不存在或未发布时抛 BusinessException。
     */
    public NoteSnapshot load(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() == null || note.getStatus() != 1) {
            throw new BusinessException("笔记不存在或未发布");
        }

        // 图片：图文笔记取 note_image，视频笔记取封面
        List<String> images = new ArrayList<>();
        if (note.getNoteType() != null && note.getNoteType() == Note.TYPE_VIDEO) {
            NoteVideo video = noteVideoMapper.selectOne(new LambdaQueryWrapper<NoteVideo>()
                    .eq(NoteVideo::getNoteId, noteId));
            if (video != null && video.getCoverUrl() != null && !video.getCoverUrl().isBlank()) {
                images.add(video.getCoverUrl());
            }
        } else {
            List<NoteImage> imageList = noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                    .eq(NoteImage::getNoteId, noteId)
                    .orderByAsc(NoteImage::getSortOrder)
                    .last("LIMIT " + MAX_IMAGES));
            for (NoteImage image : imageList) {
                if (image.getUrl() != null && !image.getUrl().isBlank()) {
                    images.add(image.getUrl());
                }
            }
        }

        // 精选评论：一级评论按热度降序
        List<NoteComment> commentList = noteCommentMapper.selectList(new LambdaQueryWrapper<NoteComment>()
                .eq(NoteComment::getNoteId, noteId)
                .isNull(NoteComment::getParentId)
                .orderByDesc(NoteComment::getHotScore)
                .orderByDesc(NoteComment::getId)
                .last("LIMIT " + MAX_COMMENTS));
        List<String> comments = new ArrayList<>();
        for (NoteComment comment : commentList) {
            if (comment.getContent() != null && !comment.getContent().isBlank()) {
                comments.add(comment.getContent().trim());
            }
        }

        return NoteSnapshot.builder()
                .noteId(noteId)
                .title(note.getTitle())
                .content(truncate(note.getContent(), MAX_CONTENT_CHARS))
                .images(images)
                .likeCount(note.getLikeCount())
                .favCount(note.getFavCount())
                .viewCount(note.getViewCount())
                .commentCount(note.getCommentCount())
                .comments(comments)
                .build();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
