package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.response.ImUserSigResponse;

public interface ImService {

    /** 生成 IM SDK 登录所需的 UserSig */
    ImUserSigResponse getUserSig(Long userId);
}
