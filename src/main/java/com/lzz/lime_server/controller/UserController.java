package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.dto.request.UpdateProfileRequest;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import com.lzz.lime_server.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户控制器
 * <p>
 * 提供与当前登录用户个人信息相关的 RESTful 接口，
 * 包括获取个人信息、更新基础资料以及上传头像/背景图。
 * </p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /// 获取当前登录用户的个人信息
    @GetMapping("/me")
    public Result<UserInfoResponse> getMyInfo() {
        return Result.success(userService.getMyInfo(currentUserId()));
    }

    /// 更新当前登录用户的资料
    @PutMapping("/me")
    public Result<UserInfoResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(currentUserId(), request));
    }

    /// 更新当前登录用户的头像
    @PostMapping("/me/avatar")
    public Result<UserInfoResponse> updateAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.updateAvatar(currentUserId(), file));
    }

    /// 更新当前登录用户的个人主页背景图
    @PostMapping("/me/background")
    public Result<UserInfoResponse> updateBackground(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.updateBackground(currentUserId(), file));
    }

    /// 从 Spring Security 上下文中获取当前已认证用户的 ID
    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
