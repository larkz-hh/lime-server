package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import com.lzz.lime_server.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 互关好友列表，供群聊拉人
    @GetMapping("/api/user/mutual-friends")
    public Result<List<UserInfoResponse>> getMutualFriends() {
        return Result.success(followService.getMutualFriends(currentUserId()));
    }

    // 关注用户
    @PostMapping("/api/user/{userId}/follow")
    public Result<Void> follow(@PathVariable Long userId) {
        followService.follow(currentUserId(), userId);
        return Result.success();
    }

    // 取消关注
    @DeleteMapping("/api/user/{userId}/follow")
    public Result<Void> unfollow(@PathVariable Long userId) {
        followService.unfollow(currentUserId(), userId);
        return Result.success();
    }

    // 关注列表（游标分页）
    @GetMapping("/api/user/{userId}/following")
    public Result<CursorPage<UserInfoResponse>> getFollowingList(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(followService.getFollowingList(userId, cursor, size, currentUserId()));
    }

    // 粉丝列表（游标分页）
    @GetMapping("/api/user/{userId}/followers")
    public Result<CursorPage<UserInfoResponse>> getFollowerList(
            @PathVariable Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size) {
        size = Math.min(size, 50);
        return Result.success(followService.getFollowerList(userId, cursor, size, currentUserId()));
    }

    // 从 Spring Security 上下文取当前登录用户 ID
    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long ? (Long) principal : null;
    }
}
