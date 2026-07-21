package com.lzz.lime_server.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口
 * <p>
 * 上传头像、个人主页背景图、笔记图片。
 * </p>
 */
public interface FileUploadService {
    String uploadAvatar(MultipartFile file);
    String uploadBackground(MultipartFile file);
    String uploadNoteImage(MultipartFile file);
}
