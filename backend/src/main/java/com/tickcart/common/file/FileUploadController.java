package com.tickcart.common.file;

import com.tickcart.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口。
 *
 * <p>路径挂在 {@code /api/admin/**} 下，由 {@code SecurityConfig} 统一要求 ROLE_ADMIN，
 * 因此无需在方法上重复标注权限。</p>
 */
@Tag(name = "文件上传", description = "后台图片上传")
@RestController
@RequestMapping("/api/admin")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Operation(summary = "上传图片",
            description = "表单字段名 file；返回可直接访问的相对 URL（如 /api/files/2026/10/07/xxx.jpg）")
    @PostMapping("/upload")
    public R<String> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(fileStorageService.store(file));
    }
}
