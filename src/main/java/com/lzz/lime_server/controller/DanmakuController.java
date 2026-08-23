package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.request.PostDanmakuRequest;
import com.lzz.lime_server.dto.response.DanmakuListResponse;
import com.lzz.lime_server.dto.response.DanmakuResponse;
import com.lzz.lime_server.service.DanmakuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuService danmakuService;

    /// 发弹幕（仅视频笔记；时间点由客户端按当前播放位置传入）
    @PostMapping("/{noteId}/danmaku")
    public Result<DanmakuResponse> postDanmaku(@PathVariable Long noteId,
                                              @Valid @RequestBody PostDanmakuRequest request) {
        return Result.success(danmakuService.postDanmaku(noteId, currentUserId(), request));
    }

    /// 拉取弹幕列表（按出现时间点升序）
    @GetMapping("/{noteId}/danmaku")
    public Result<DanmakuListResponse> listDanmaku(@PathVariable Long noteId) {
        return Result.success(danmakuService.listDanmaku(noteId));
    }

    /// 删除弹幕（弹幕发送者本人或视频笔记作者）
    @DeleteMapping("/{noteId}/danmaku/{danmakuId}")
    public Result<Void> deleteDanmaku(@PathVariable Long noteId, @PathVariable Long danmakuId) {
        danmakuService.deleteDanmaku(noteId, danmakuId, currentUserId());
        return Result.success();
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
