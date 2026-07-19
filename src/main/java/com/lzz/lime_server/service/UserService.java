package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.UpdateProfileRequest;
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
    UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request);
    UserInfoResponse updateAvatar(Long userId, MultipartFile file);
    UserInfoResponse updateBackground(Long userId, MultipartFile file);
}
