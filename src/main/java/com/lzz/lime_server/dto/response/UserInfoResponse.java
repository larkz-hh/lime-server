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
}
