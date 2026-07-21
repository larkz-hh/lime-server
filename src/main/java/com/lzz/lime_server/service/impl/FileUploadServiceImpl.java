package com.lzz.lime_server.service.impl;

import com.lzz.lime_server.common.exception.BusinessException;
import com.lzz.lime_server.service.FileUploadService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务实现类
 * <p>
 * 上传头像、背景图、笔记图片
 * </p>

 */
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final MinioClient minioClient;

    @Value("${minio.endpoint}")
    private String endpoint;

    // 返回给客户端的公网地址，默认回退到 endpoint
    @Value("${minio.public-endpoint:${minio.endpoint}}")
    private String publicEndpoint;

    @Value("${minio.bucket-name}")
    private String bucketName;

    // 大小限制
    private static final long MAX_SIZE_5M = 5 * 1024 * 1024L;
    private static final long MAX_SIZE_10M = 10 * 1024 * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /**
     * 上传用户头像
     * <p>
     * 执行文件校验后，将文件流式传输至 MinIO 的 avatars/ 目录下，
     * 返回拼接好的公网访问 URL。
     * </p>
     *
     * @param file 上传的头像图片文件
     * @return 头像图片的公网访问 URL
     * @throws BusinessException 当文件为空、格式不合法、超过大小限制或上传失败时抛出
     */
    @Override
    public String uploadAvatar(MultipartFile file) {
        return doUpload(file, "avatars", MAX_SIZE_5M, "头像");
    }

    /**
     * 上传用户个人主页背景图
     * <p>
     * 执行文件校验后，将文件流式传输至 MinIO 的 backgrounds/ 目录下，
     * 并返回拼接好的公网访问 URL。
     * </p>
     *
     * @param file 上传的背景图片文件
     * @return 背景图片的公网访问 URL
     * @throws BusinessException 当文件为空、格式不合法、超过大小限制或上传失败时抛出
     */
    @Override
    public String uploadBackground(MultipartFile file) {
        return doUpload(file, "backgrounds", MAX_SIZE_5M, "背景图");
    }

    /**
     * 上传笔记图片
     * <p>
     * 执行文件校验后，将文件流式传输至 MinIO 的 notes/ 目录下，
     * 返回拼接好的公网访问 URL，供发布笔记时引用。
     * </p>
     *
     * @param file 上传的笔记图片文件
     * @return 笔记图片的公网访问 URL
     * @throws BusinessException 当文件为空、格式不合法、超过大小限制或上传失败时抛出
     */
    @Override
    public String uploadNoteImage(MultipartFile file) {
        return doUpload(file, "notes", MAX_SIZE_10M, "笔记图片");
    }

    /**
     * 通用文件上传方法
     * <p>
     * 校验文件合法性后上传至 MinIO，返回公网访问 URL。
     * 由各具体上传方法调用，通过 folder / maxSize / label 参数区分业务场景。
     * </p>
     *
     * @param file    上传的文件
     * @param folder  MinIO 中的存储目录（如 avatars、notes）
     * @param maxSize 允许的最大文件字节数
     * @param label   用于错误提示的业务名称（如"头像"、"笔记图片"）
     * @return 文件的公网访问 URL
     * @throws BusinessException 当文件为空、格式不合法、超过大小限制或上传失败时抛出
     */
    private String doUpload(MultipartFile file, String folder, long maxSize, String label) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("只支持 JPG、PNG、WebP、GIF 格式");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(label + "文件不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        // 提取 MIME 类型中的后缀
        String ext = contentType.substring(contentType.lastIndexOf('/') + 1);
        if ("jpeg".equals(ext)) ext = "jpg";
        String objectName = folder + "/" + UUID.randomUUID() + "." + ext;
        //eg:  "avatars/550e8400-e29b-41d4-a716-446655440000.jpg"
        // MinIO 文件上传
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
        // 检查配置的公网地址末尾是否已经包含了 /
        String base = publicEndpoint.endsWith("/") ? publicEndpoint : publicEndpoint + "/";
        return base + bucketName + "/" + objectName;
    }
}
