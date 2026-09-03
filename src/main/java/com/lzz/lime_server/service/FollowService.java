package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.response.CursorPage;
import com.lzz.lime_server.dto.response.UserInfoResponse;

public interface FollowService {

    // 关注（幂等）
    void follow(Long followerId, Long followeeId);

    // 取消关注（幂等）
    void unfollow(Long followerId, Long followeeId);

    // 某用户的关注列表
    CursorPage<UserInfoResponse> getFollowingList(Long userId, Long cursor, int size, Long currentUserId);

    // 某用户的粉丝列表
    CursorPage<UserInfoResponse> getFollowerList(Long userId, Long cursor, int size, Long currentUserId);
}
