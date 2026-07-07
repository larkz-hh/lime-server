package com.lzz.lime_server.dto.response;

import lombok.Data;

@Data
public class UserInfoResponse {

    private Long id;
    private String username;
    private String email;
    private String phone;
    private String avatar;
    private String role;
}
