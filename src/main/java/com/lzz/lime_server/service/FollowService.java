package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.UserInfoResponse;

import java.util.List;

public interface FollowService {

    // 关注（幂等）
    void follow(Long followerId, Long followeeId);

    // 取消关注（幂等）
    void unfollow(Long followerId, Long followeeId);

    // 某用户的关注列表
    CursorPage<UserInfoResponse> getFollowingList(Long userId, Long cursor, int size, Long currentUserId);

    // 某用户的粉丝列表
    CursorPage<UserInfoResponse> getFollowerList(Long userId, Long cursor, int size, Long currentUserId);

    // 互关好友列表，供群聊拉人
    List<UserInfoResponse> getMutualFriends(Long userId);

    // 是否互关
    boolean isMutual(Long userId, Long targetUserId);

    // 私信门槛：双方须互关
    void requireMutual(Long userId, Long targetUserId);
}
