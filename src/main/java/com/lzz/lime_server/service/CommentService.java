package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.PublishCommentRequest;
import com.lzz.lime_server.dto.response.CommentResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.ReplyResponse;

public interface CommentService {

    /// 发布一级评论
    CommentResponse publishComment(Long noteId, Long userId,
                                   PublishCommentRequest request, String ipAddress);

    /// 发布回复（二级）
    ReplyResponse publishReply(Long noteId, Long parentId, Long userId,
                               PublishCommentRequest request, String ipAddress);

    /// 获取笔记的一级评论列表
    CursorPage<CommentResponse> getComments(Long noteId, String sort, String cursor,
                                            int size, Long currentUserId);

    //// 获取某条一级评论的全部回复，时间正序
    CursorPage<ReplyResponse> getReplies(Long commentId, Long cursor,
                                         int size, Long currentUserId);

    /// 点赞评论/回复，幂等操作
    void likeComment(Long commentId, Long userId);

    /// 取消点赞，幂等操作
    void unlikeComment(Long commentId, Long userId);

    /// 删除评论或回复
    void deleteComment(Long commentId, Long userId);
}
