package com.lzz.lime_server.controller;

import com.lzz.lime_server.common.Result;
import com.lzz.lime_server.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 群数据托管在腾讯云 IM，后端仅提供群头像上传等辅助能力。
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
public class GroupController {

    private final FileUploadService fileUploadService;

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(fileUploadService.uploadGroupAvatar(file));
    }
}
