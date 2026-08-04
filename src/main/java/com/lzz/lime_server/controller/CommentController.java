package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.common.util.IpUtil;
import com.lzz.lime_server.dto.request.PublishCommentRequest;
import com.lzz.lime_server.dto.response.CommentResponse;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.ReplyResponse;
import com.lzz.lime_server.service.CommentService;
import com.lzz.lime_server.service.FileUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final FileUploadService fileUploadService;

    /// 发布一级评论
    @PostMapping("/api/notes/{noteId}/comments")
    public Result<CommentResponse> publishComment(
            @PathVariable Long noteId,
            @RequestBody PublishCommentRequest request,
            HttpServletRequest httpRequest) {
        CommentResponse resp = commentService.publishComment(
                noteId, currentUserId(), request, IpUtil.getClientIp(httpRequest));
        return Result.success(resp);
    }

    /// 发布回复（二级评论）
    @PostMapping("/api/notes/{noteId}/comments/{commentId}/replies")
    public Result<ReplyResponse> publishReply(
            @PathVariable Long noteId,
            @PathVariable Long commentId,
            @RequestBody PublishCommentRequest request,
            HttpServletRequest httpRequest) {
        ReplyResponse resp = commentService.publishReply(
                noteId, commentId, currentUserId(), request, IpUtil.getClientIp(httpRequest));
        return Result.success(resp);
    }

    /// 获取笔记的一级评论列表；sort=hot（热度降序，默认）或 sort=time（最新在前）
    @GetMapping("/api/notes/{noteId}/comments")
    public Result<CursorPage<CommentResponse>> getComments(
            @PathVariable Long noteId,
            @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(commentService.getComments(noteId, sort, cursor, size, currentUserId()));
    }

    /// 获取某条评论的全部回复，时间正序（最早在顶部，最新在底部）
    @GetMapping("/api/comments/{commentId}/replies")
    public Result<CursorPage<ReplyResponse>> getReplies(
            @PathVariable Long commentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        return Result.success(commentService.getReplies(commentId, cursor, size, currentUserId()));
    }

    /// 点赞评论或回复
    @PostMapping("/api/comments/{commentId}/like")
    public Result<Void> likeComment(@PathVariable Long commentId) {
        commentService.likeComment(commentId, currentUserId());
        return Result.success();
    }

    /// 取消点赞
    @DeleteMapping("/api/comments/{commentId}/like")
    public Result<Void> unlikeComment(@PathVariable Long commentId) {
        commentService.unlikeComment(commentId, currentUserId());
        return Result.success();
    }

    /// 删除评论或回复（评论者本人或笔记作者可删）
    @DeleteMapping("/api/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId, currentUserId());
        return Result.success();
    }

    /// 上传评论图片，返回 url；发布评论时带上该 url 即可
    @PostMapping("/api/comments/images")
    public Result<Map<String, String>> uploadCommentImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.uploadCommentImage(file);
        return Result.success(Map.of("url", url));
    }

    /// 上传评论语音，返回 url；同时由客户端提供 duration（秒）一并在发布评论时传入
    @PostMapping("/api/comments/voices")
    public Result<Map<String, String>> uploadCommentVoice(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.uploadCommentVoice(file);
        return Result.success(Map.of("url", url));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
