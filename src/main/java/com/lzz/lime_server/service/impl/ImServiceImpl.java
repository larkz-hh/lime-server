package com.lzz.lime_server.service.impl;

import com.lzz.lime_server.config.ImProperties;
import com.lzz.lime_server.dto.response.ImUserSigResponse;
import com.lzz.lime_server.service.ImService;
import com.lzz.lime_server.util.TLSSigAPIv2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImProperties imProperties;

    @Override
    public ImUserSigResponse getUserSig(Long userId) {
        String imUserId = imProperties.toImUserId(userId);
        long expire = System.currentTimeMillis() / 1000 + imProperties.getUserSigExpire();
        String userSig = new TLSSigAPIv2(imProperties.getSdkAppId(), imProperties.getSecretKey())
                .genSig(imUserId, imProperties.getUserSigExpire());
        return new ImUserSigResponse(imProperties.getSdkAppId(), imUserId, userSig, expire);
    }
}
