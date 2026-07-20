package com.lzz.lime_server.service.impl;

import com.lzz.lime_server.common.ResultCode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.ChangePasswordRequest;
import com.lzz.lime_server.dto.request.DeleteAccountRequest;
import com.lzz.lime_server.dto.request.UpdateProfileRequest;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import com.lzz.lime_server.entity.User;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.AuthService;
import com.lzz.lime_server.service.FileUploadService;
import com.lzz.lime_server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    // Redis Key 前缀
    private static final String EMAIL_CODE_KEY_PREFIX = "email:code:";

    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;

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
     * 修改当前用户密码
     * <p>
     * 支持两种身份验证方式：
     * - 原密码验证：直接比对 BCrypt 哈希
     * - 邮箱验证码验证：校验 Redis 中存储的验证码
     * 验证通过后将新密码加密存储，并立即使当前 Token 失效，要求用户重新登录。
     * </p>
     *
     * @param userId      用户唯一标识 ID
     * @param accessToken 当前请求携带的 Access Token，修改成功后加入黑名单
     * @param request     包含新密码以及原密码或验证码的请求对象
     * @throws BusinessException 当用户不存在、原密码/验证码错误，或两者均为空时抛出
     */
    @Override
    public void changePassword(Long userId, String accessToken, ChangePasswordRequest request) {
        if (request.getOldPassword() == null && request.getCode() == null) {
            throw new BusinessException("原密码或验证码不能同时为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        if (request.getCode() != null) {
            // 验证码验证：从 Redis 取出与该邮箱绑定的验证码进行比对
            String codeKey = EMAIL_CODE_KEY_PREFIX + user.getEmail();
            String storedCode = redisTemplate.opsForValue().get(codeKey);
            if (storedCode == null || !storedCode.equals(request.getCode())) {
                throw new BusinessException("验证码错误或已过期");
            }
            redisTemplate.delete(codeKey);
        } else {
            // 原密码验证
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new BusinessException("原密码错误");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        // 修改密码后使当前 Token 失效，强制重新登录
        authService.logout(userId, accessToken);
    }

    /**
     * 注销当前用户账号（软删除）
     * <p>
     * 验证密码后将账号标记为已删除（deleted=1），并使当前 Token 立即失效。
     * 软删除不会物理删除数据库记录，由 MyBatis-Plus @TableLogic 自动处理。
     * </p>
     *
     * @param userId      用户唯一标识 ID
     * @param accessToken 当前请求携带的 Access Token，注销成功后加入黑名单
     * @param request     包含账号密码的确认请求对象
     * @throws BusinessException 当用户不存在或密码错误时抛出
     */
    @Override
    public void deleteAccount(Long userId, String accessToken, DeleteAccountRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        // 先使 Token 失效，再软删除账号
        authService.logout(userId, accessToken);
        userMapper.deleteById(userId);
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
