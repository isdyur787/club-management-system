package com.club.management.file.service.impl;

import com.club.management.common.exception.BusinessException;
import com.club.management.common.exception.ErrorCode;
import com.club.management.common.util.SecurityUtil;
import com.club.management.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload.dir:data/uploads}")
    private String uploadDir;

    @Override
    public Map<String, Object> upload(MultipartFile file, String type) {
        Long userId = SecurityUtil.getCurrentUserId();

        // 校验文件类型
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";

        if ("photo".equals(type)) {
            if (!extension.matches("(?i)\\.(jpg|jpeg|png|webp)")) {
                throw new BusinessException(ErrorCode.FILE_FORMAT_NOT_SUPPORTED);
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
            }
        } else {
            if (!extension.matches("(?i)\\.(txt|md|docx)")) {
                throw new BusinessException(ErrorCode.FILE_FORMAT_NOT_SUPPORTED);
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
            }
        }

        // 生成存储路径
        String relativePath = type + "/" + userId + "/" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        try {
            // 确保使用绝对路径
            Path targetPath = Paths.get(System.getProperty("user.dir"), uploadDir, relativePath);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
            log.info("文件上传成功: {}", targetPath);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("url", "/api/v1/files/" + relativePath);
        result.put("fileName", originalFilename);
        result.put("fileSize", file.getSize());
        return result;
    }
}
