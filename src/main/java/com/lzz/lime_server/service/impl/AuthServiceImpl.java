package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzz.lime_server.common.ResultCode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.request.LoginRequest;
import com.lzz.lime_server.dto.request.RefreshTokenRequest;
import com.lzz.lime_server.dto.request.RegisterRequest;
import com.lzz.lime_server.dto.response.LoginResponse;
import com.lzz.lime_server.entity.User;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.AuthService;
import com.lzz.lime_server.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类
 * 负责处理用户注册、登录、登出以及 Token 刷新的核心业务逻辑
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.expire}")
    private long expire;

    @Value("${jwt.refresh-expire}")
    private long refreshExpire;

    // Redis 中存储 Refresh Token 的键前缀
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:token:";
    // Redis 中存储 Access Token 黑名单的键前缀
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:token:";


    /**
     * 用户注册
     * 校验用户名和邮箱的唯一性，对密码进行 BCrypt 加密后存入数据库
     *
     * @param request 包含用户名、密码、邮箱等注册信息的请求对象
     * @throws BusinessException 当用户名或邮箱已被注册时抛出
     */
    @Override
    public void register(RegisterRequest request) {
        // 用户名唯一校验
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (usernameCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 邮箱唯一校验
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (emailCount > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // 使用 BCrypt 加密后存入数据库
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole("DEFAULT");// 设置默认角色
        user.setStatus(0);// 设置默认状态（0: 正常, 1: 封禁等）

        userMapper.insert(user);
    }


    /**
     * 用户登录
     * 校验用户名、密码及账号状态；如果校验通过，生成双 Token 并返回
     *
     * @param request 包含用户名和密码的请求对象
     * @return 包含 Access Token、Refresh Token 及过期时间的登录响应对象
     * @throws BusinessException 当用户名或密码错误，或账号被封禁时抛出
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 根据用户名查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        // 校验用户是否存在，以及密码是否匹配
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 校验用户状态
        if (user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        // 登录成功，调用私有方法生成双 Token 并返回
        return buildLoginResponse(user.getId());
    }


    /**
     * 用户登出
     * J通过 Redis 黑名单让 Access Token 提前失效，并删除 Refresh Token 阻断续期
     *
     * @param userId 当前登出用户的唯一标识
     * @param accessToken 当前需要作废的访问令牌
     */
    @Override
    public void logout(Long userId, String accessToken) {
        //  从 Redis 中删除该用户的 Refresh Token
        redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + userId);

        // Access Token 加入黑名单
        // 获取该 Token 距离过期还剩多少毫秒
        long remainMillis = jwtUtil.getRemainingExpireMillis(accessToken);
        // 如果 Token 还没过期，存入 Redis 黑名单，设置与剩余有效期相同的过期时间
        if (remainMillis > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + accessToken,
                    "1",
                    remainMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 刷新 Token
     * 验证 Refresh Token 的合法性，确保是服务端最新签发，防止重放攻击
     *
     * @param request 包含 Refresh Token 的请求对象
     * @return 包含新签发的 Access Token 和 Refresh Token 的响应对象
     * @throws BusinessException 当 Refresh Token 无效、过期或与 Redis 中记录不一致时抛出
     */
    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        // 验证 Refresh Token 的签名和过期时间
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 从 Refresh Token 中解析出用户 ID
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);

        // 比对 Redis 中存储的 Refresh Token
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + userId);
        if (!refreshToken.equals(storedToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 验证通过，重新生成双 Token 并返回
        return buildLoginResponse(userId);
    }

    /**
     * 统一构建登录/刷新 Token 的响应对象
     * 将生成 Token 和存储 Refresh Token 到 Redis 的逻辑封装，避免代码重复
     *
     * @param userId 需要生成 Token 的用户唯一标识
     * @return 封装好的包含双 Token 的响应对象
     */
    private LoginResponse buildLoginResponse(Long userId) {
        // 生成 Access Token 和 Refresh Token
        String accessToken = jwtUtil.generateAccessToken(userId);
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // 存储 Refresh Token 到 Redis，置过期时间
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + userId,
                refreshToken,
                refreshExpire,
                TimeUnit.SECONDS);

        return new LoginResponse(accessToken, refreshToken, expire);
    }
}
