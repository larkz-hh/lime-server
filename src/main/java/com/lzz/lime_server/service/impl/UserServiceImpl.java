package com.lzz.lime_server.service.impl;

import com.lzz.lime_server.common.ResultCode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.UpdateProfileRequest;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import com.lzz.lime_server.entity.User;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.FileUploadService;
import com.lzz.lime_server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


/**
 * 用户服务实现类
 * <p>
 * 负责处理与用户个人信息相关的核心业务逻辑，
 * 包括用户信息查询、基础资料更新以及头像/背景图的上传与更新。
 * </p>
 *
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;

    /**
     * 获取指定用户的个人信息
     *
     * @param userId 用户唯一标识 ID
     * @return 封装后的用户信息响应对象 (UserInfoResponse)
     * @throws BusinessException 当用户不存在时抛出 NOT_FOUND 异常
     */
    @Override
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toResponse(user);
    }

    /**
     * 更新用户基础资料（支持部分更新）
     * <p>
     * 仅更新请求中非空且有效的字段：
     * - nickname: 仅当传入非空文本时更新，防止误清空。
     * - bio / region: 允许传入空字符串("")来清空内容，传入 null 则保持原值。
     * - gender / birthday: 仅当传入非 null 值时更新。
     * 若没有任何有效字段变更，则跳过数据库更新操作以优化性能。
     * </p>
     *
     * @param userId  用户唯一标识 ID
     * @param request 包含待更新用户资料的请求对象
     * @return 更新成功后的最新用户信息响应对象
     * @throws BusinessException 当用户不存在时抛出 NOT_FOUND 异常
     */
    @Override
    public UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        boolean changed = false;
        // 仅当传入有效文本时才更新
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
            changed = true;
        }
        // bio 允许传空字符串来清空
        if (request.getBio() != null) {
            user.setBio(request.getBio());
            changed = true;
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
            changed = true;
        }
        if (request.getBirthday() != null) {
            user.setBirthday(request.getBirthday());
            changed = true;
        }
        // region 允许传空字符串来清空
        if (request.getRegion() != null) {
            user.setRegion(request.getRegion());
            changed = true;
        }
        // 更新用户信息
        if (changed) {
            userMapper.updateById(user);
        }
        return toResponse(user);
    }

    /**
     * 更新用户头像
     * <p>
     * 将前端上传的图片文件存储至对象存储，
     * 获取生成的图片 URL 后更新至用户表。
     * </p>
     * @param userId 用户唯一标识 ID
     * @param file   前端上传的头像图片文件
     * @return 更新成功后的最新用户信息响应对象
     * @throws BusinessException 当用户不存在时抛出 NOT_FOUND 异常
     */
    @Override
    public UserInfoResponse updateAvatar(Long userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        String avatarUrl = fileUploadService.uploadAvatar(file);
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return toResponse(user);
    }

    /**
     * 更新用户个人主页背景图
     * <p>
     * 将前端上传的背景图片文件存储至对象存储，
     * 获取生成的图片 URL 后更新至用户表。
     * </p>
     * @param userId 用户唯一标识 ID
     * @param file   前端上传的背景图片文件
     * @return 更新成功后的最新用户信息响应对象
     * @throws BusinessException 当用户不存在时抛出 NOT_FOUND 异常
     */
    @Override
    public UserInfoResponse updateBackground(Long userId, MultipartFile file) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        String backgroundUrl = fileUploadService.uploadBackground(file);
        user.setBackgroundImage(backgroundUrl);
        userMapper.updateById(user);
        return toResponse(user);
    }


    /**
     * 将数据库 User 实体转换为对外暴露的 UserInfoResponse DTO
     * <p>
     * 手动映射字段来实现数据隔离，防止密码等敏感信息泄露给客户端
     * </p>
     * @param user 数据库用户实体对象
     * @return 转换后的用户信息响应对象
     */
    private UserInfoResponse toResponse(User user) {
        UserInfoResponse resp = new UserInfoResponse();
        resp.setId(user.getId());
        resp.setEmail(user.getEmail());
        resp.setNickname(user.getNickname());
        resp.setHandle(user.getHandle());
        resp.setBio(user.getBio());
        resp.setAvatar(user.getAvatar());
        resp.setBackgroundImage(user.getBackgroundImage());
        resp.setGender(user.getGender());
        resp.setBirthday(user.getBirthday());
        resp.setRegion(user.getRegion());
        resp.setRole(user.getRole());
        return resp;
    }
}
