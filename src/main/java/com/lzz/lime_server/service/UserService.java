package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.ChangePasswordRequest;
import com.lzz.lime_server.dto.request.DeleteAccountRequest;
import com.lzz.lime_server.dto.request.UpdateProfileRequest;
import com.lzz.lime_server.dto.response.LoginResponse;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 * <p>
 * 用户信息查询、基础资料更新以及头像/背景图的上传与更新。
 * </p>
 */
public interface UserService {
    UserInfoResponse getMyInfo(Long userId);
    UserInfoResponse getUserProfile(Long targetUserId, Long currentUserId);
    UserInfoResponse getUserByHandle(String handle, Long currentUserId);
    UserInfoResponse getUserByUid(String uid, Long currentUserId);
    UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request);
    UserInfoResponse updateAvatar(Long userId, MultipartFile file);
    UserInfoResponse updateBackground(Long userId, MultipartFile file);
    /**
     * 修改当前用户密码。
     * 成功后其它会话 token 全部失效并收到 password_changed 的 kick；
     * 当前会话不登出，返回新双 Token 无缝续用。
     */
    LoginResponse changePassword(Long userId, ChangePasswordRequest request);
    void deleteAccount(Long userId, String accessToken, DeleteAccountRequest request);
}
