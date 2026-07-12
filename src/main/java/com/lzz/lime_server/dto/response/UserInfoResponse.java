package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class UserInfoResponse {

    private Long id;
    private String email;
    private String nickname;
    private String handle;
    private String bio;
    private String avatar;
    private String role;
}
