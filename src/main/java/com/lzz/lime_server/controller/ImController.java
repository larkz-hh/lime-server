package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.config.ImProperties;
import com.lzz.lime_server.dto.request.ConversationOpenRequest;
import com.lzz.lime_server.dto.response.ConversationOpenResponse;
import com.lzz.lime_server.dto.response.ImUserSigResponse;
import com.lzz.lime_server.service.FollowService;
import com.lzz.lime_server.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * UserSig 下发与私信会话授权接口。
 */
@RestController
@RequestMapping("/api/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;
    private final FollowService followService;
    private final ImProperties imProperties;

    /** App 端 IM SDK 登录凭证 */
    @GetMapping("/userSig")
    public Result<ImUserSigResponse> getUserSig(@AuthenticationPrincipal Long userId) {
        return Result.success(imService.getUserSig(userId));
    }

    /**
     * 仅互关双方可发起私信，否则返回 403。
     */
    @PostMapping("/conversation/open")
    public Result<ConversationOpenResponse> openConversation(@AuthenticationPrincipal Long userId,
                                                             @RequestBody ConversationOpenRequest request) {
        followService.requireMutual(userId, request.getTargetUserId());
        String conversationId = "c2c_" + imProperties.toImUserId(request.getTargetUserId());
        return Result.success(new ConversationOpenResponse(conversationId));
    }
}
