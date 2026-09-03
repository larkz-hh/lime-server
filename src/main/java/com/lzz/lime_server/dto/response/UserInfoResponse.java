package com.lzz.lime_server.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserInfoResponse {

    private Long id;
    private String email;
    private String nickname;
    private String handle;
    private String bio;
    private String avatar;
    private String backgroundImage;
    /** 性别：0=未设置，1=男，2=女 */
    private Integer gender;
    private LocalDate birthday;
    private String region;
    private String role;
    /** 点赞列表是否对外公开：false=公开，true=私密 */
    private Boolean likePrivate;
    /** 收藏列表是否对外公开：false=公开，true=私密 */
    private Boolean favPrivate;
    private Long followingCount;
    private Long followerCount;
    private Long noteCount;
    private Long totalLikeCount;
    private Long totalFavCount;
    private Boolean isFollowing;
    private Boolean isFollowedBack;
}
