package com.lzz.lime_server.service;

import com.lzz.lime_server.dto.request.PostDanmakuRequest;
import com.lzz.lime_server.dto.response.DanmakuListResponse;
import com.lzz.lime_server.dto.response.DanmakuResponse;

public interface DanmakuService {

    DanmakuResponse postDanmaku(Long noteId, Long userId, PostDanmakuRequest request);

    DanmakuListResponse listDanmaku(Long noteId);

    void deleteDanmaku(Long noteId, Long danmakuId, Long currentUserId);
}
