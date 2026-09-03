package com.lzz.lime_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzz.lime_server.common.ResultCode;
import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.UserInfoResponse;
import com.lzz.lime_server.entity.Notification;
import com.lzz.lime_server.entity.UserFollow;
import com.lzz.lime_server.mapper.UserFollowMapper;
import com.lzz.lime_server.mapper.UserMapper;
import com.lzz.lime_server.service.FollowService;
import com.lzz.lime_server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注服务实现类
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    /**
     * 关注，成功后给被关注者发关注通知
     */
    @Override
    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException("不能关注自己");
        }
        if (userMapper.selectById(followeeId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        long exists = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (exists > 0) return;

        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        userFollowMapper.insert(follow);

        notificationService.notifyUser(followerId, followeeId, Notification.TYPE_FOLLOW, null, null, null);
    }

    /**
     * 取消关注
     */
    @Override
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
    }

    /**
     * 获取关注列表
     */
    @Override
    public CursorPage<UserInfoResponse> getFollowingList(Long userId, Long cursor, int size, Long currentUserId) {
        List<UserFollowMapper.UserBriefRow> rows = userFollowMapper.selectFollowing(userId, cursor, size + 1);
        return buildPage(rows, size, currentUserId);
    }

    /**
     * 粉丝列表
     */
    @Override
    public CursorPage<UserInfoResponse> getFollowerList(Long userId, Long cursor, int size, Long currentUserId) {
        List<UserFollowMapper.UserBriefRow> rows = userFollowMapper.selectFollowers(userId, cursor, size + 1);
        return buildPage(rows, size, currentUserId);
    }

    /**
     * 列表行组装分页
     */
    private CursorPage<UserInfoResponse> buildPage(
            List<UserFollowMapper.UserBriefRow> rows, int size, Long currentUserId) {
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);

        List<UserInfoResponse> items = rows.stream().map(this::toBrief).toList();

        // 批量查询关注关系
        List<Long> userIds = items.stream().map(UserInfoResponse::getId).toList();
        if (!userIds.isEmpty() && currentUserId != null) {
            Set<Long> followedIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFollowerId, currentUserId)
                                    .in(UserFollow::getFolloweeId, userIds))
                    .stream().map(UserFollow::getFolloweeId).collect(Collectors.toSet());
            Set<Long> followedBackIds = userFollowMapper.selectList(
                            new LambdaQueryWrapper<UserFollow>()
                                    .eq(UserFollow::getFolloweeId, currentUserId)
                                    .in(UserFollow::getFollowerId, userIds))
                    .stream().map(UserFollow::getFollowerId).collect(Collectors.toSet());
            items.forEach(item -> {
                item.setIsFollowing(followedIds.contains(item.getId()));
                item.setIsFollowedBack(followedBackIds.contains(item.getId()));
            });
        }

        Long nextCursor = hasMore ? rows.getLast().getCursorId() : null;
        return CursorPage.of(items, nextCursor, hasMore);
    }

    /**
     * 列表行转用户简要信息
     */
    private UserInfoResponse toBrief(UserFollowMapper.UserBriefRow row) {
        UserInfoResponse resp = new UserInfoResponse();
        resp.setId(row.getId());
        resp.setNickname(row.getNickname());
        resp.setHandle(row.getHandle());
        resp.setAvatar(row.getAvatar());
        resp.setBio(row.getBio());
        return resp;
    }
}
