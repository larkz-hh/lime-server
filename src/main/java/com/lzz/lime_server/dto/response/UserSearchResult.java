package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class UserSearchResult {

    private Long id;
    private String nickname;
    private String handle;
    private String avatar;
    /** 是否为当前登录用户本人 */
    private Boolean isMe;
    private Boolean isFollowing;
    private Boolean isFollowedBack;
    private Long followerCount;
}
