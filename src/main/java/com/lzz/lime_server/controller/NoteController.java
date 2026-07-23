package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.request.PublishNoteRequest;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.NoteFeedResponse;
import com.lzz.lime_server.dto.response.NoteResponse;
import com.lzz.lime_server.service.FileUploadService;
import com.lzz.lime_server.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /// 首页信息流，Cursor 分页
    @GetMapping("/feed")
    public Result<CursorPage<NoteFeedResponse>> getFeed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(noteService.getFeed(cursor, size));
    }

    /// 发布图文笔记
    @PostMapping
    public Result<NoteResponse> publishNote(@Valid @RequestBody PublishNoteRequest request) {
        NoteResponse resp = noteService.publishNote(currentUserId(), request);
        return Result.success(resp);
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
