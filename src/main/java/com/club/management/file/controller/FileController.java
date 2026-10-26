package com.club.management.file.controller;

import com.club.management.common.annotation.OperationLog;
import com.club.management.common.annotation.RequirePermission;
import com.club.management.common.response.R;
import com.club.management.file.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    @RequirePermission("file:upload")
    @OperationLog(module = "文件管理", operation = "文件上传")
    public R<Map<String, Object>> upload(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "document") String type) {
        return R.ok(fileService.upload(file, type));
    }
}
