package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NoteDetailResponse;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteResponse;
import com.lzz.lime_server.service.FileUploadService;
import com.lzz.lime_server.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final FileUploadService fileUploadService;

    /// 上传单张笔记图片，返回可用于发布笔记的图片 URL
    @PostMapping("/images")
    public Result<Map<String, String>> uploadNoteImage(@RequestParam("file") MultipartFile file) {
        String url = fileUploadService.uploadNoteImage(file);
        return Result.success(Map.of("url", url));
    }

    /// 获取指定用户的笔记列表，Cursor 分页
    @GetMapping("/user/{userId}")
    public Result<CursorPage<NoteFeedResponse>> getUserNotes(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "published") String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        if (!"published".equals(status) && !"draft".equals(status)) {
            throw new BusinessException("status 参数非法，可选值：published / draft");
        }
        int statusVal = "draft".equals(status) ? 0 : 1;
        size = Math.min(size, 50);
        return Result.success(noteService.getUserNotes(userId, statusVal, cursor, size, currentUserId()));
    }

    /// 首页信息流，Cursor 分页
    @GetMapping("/feed")
    public Result<CursorPage<NoteFeedResponse>> getFeed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(noteService.getFeed(cursor, size, currentUserId()));
    }

    /// 发布图文笔记
    @PostMapping
    public Result<NoteResponse> publishNote(@Valid @RequestBody PublishNoteRequest request) {
        NoteResponse resp = noteService.publishNote(currentUserId(), request);
        return Result.success(resp);
    }

    /// 获取笔记详情
    @GetMapping("/{id}")
    public Result<NoteDetailResponse> getNoteDetail(@PathVariable Long id) {
        return Result.success(noteService.getNoteDetail(id, currentUserId()));
    }

    /// 点赞笔记
    @PostMapping("/{id}/like")
    public Result<Void> likeNote(@PathVariable Long id) {
        noteService.likeNote(id, currentUserId());
        return Result.success();
    }

    /// 取消点赞
    @DeleteMapping("/{id}/like")
    public Result<Void> unlikeNote(@PathVariable Long id) {
        noteService.unlikeNote(id, currentUserId());
        return Result.success();
    }

    /// 收藏笔记
    @PostMapping("/{id}/favorite")
    public Result<Void> favoriteNote(@PathVariable Long id) {
        noteService.favoriteNote(id, currentUserId());
        return Result.success();
    }

    /// 取消收藏
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavoriteNote(@PathVariable Long id) {
        noteService.unfavoriteNote(id, currentUserId());
        return Result.success();
    }

    /// 获取指定用户点赞过的笔记列表，Cursor 分页；若对方开启点赞隐私则返回业务错误
    @GetMapping("/user/{userId}/likes")
    public Result<CursorPage<NoteFeedResponse>> getLikedNotes(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(noteService.getLikedNotes(userId, cursor, size, currentUserId()));
    }

    /// 获取指定用户收藏的笔记列表，Cursor 分页；若对方开启收藏隐私则返回业务错误
    @GetMapping("/user/{userId}/favorites")
    public Result<CursorPage<NoteFeedResponse>> getFavoritedNotes(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(noteService.getFavoritedNotes(userId, cursor, size, currentUserId()));
    }

    /// 获取当前用户浏览历史，Cursor 分页（cursor 为 epoch 毫秒）
    @GetMapping("/history")
    public Result<CursorPage<NoteFeedResponse>> getViewHistory(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(noteService.getViewedNotes(currentUserId(), cursor, size));
    }

    /// 批量删除浏览历史中的指定笔记记录
    @DeleteMapping("/history")
    public Result<Void> deleteViewRecords(@RequestBody DeleteHistoryRequest request) {
        noteService.deleteViewRecords(currentUserId(), request.noteIds());
        return Result.success();
    }

    record DeleteHistoryRequest(List<Long> noteIds) {}

    /// 清空当前用户全部浏览历史
    @DeleteMapping("/history/all")
    public Result<Void> clearViewHistory() {
        noteService.clearViewHistory(currentUserId());
        return Result.success();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
