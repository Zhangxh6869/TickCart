package com.tickcart.common.file;

import com.tickcart.common.api.ResultCode;
import com.tickcart.common.config.UploadProperties;
import com.tickcart.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片存储服务：把上传的图片落到本地磁盘，返回可直接访问的相对 URL。
 *
 * <p>安全约定：扩展名**只由 Content-Type 白名单推导**，绝不使用客户端原始文件名，
 * 因此路径穿越（{@code ../../evil.jsp}）与可执行脚本上传都无从下手；
 * 落盘目录按 {@code yyyy/MM/dd} 分片，避免单目录文件过多。</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 允许的图片 MIME 类型 → 落盘扩展名（白名单，其余一律拒绝）。 */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final UploadProperties properties;

    /** 落盘根目录（绝对路径，已 normalize，防止 {@code ..} 影响）。 */
    private final Path rootDir;

    public FileStorageService(UploadProperties properties) {
        this.properties = properties;
        this.rootDir = Path.of(properties.getDir()).toAbsolutePath().normalize();
        // 启动即建目录，保证静态资源映射指向的路径真实存在
        try {
            Files.createDirectories(rootDir);
        } catch (IOException ex) {
            log.warn("上传目录创建失败，首次上传时会重试：{}", rootDir, ex);
        }
    }

    /**
     * 保存图片并返回可访问的相对 URL。
     *
     * @param file 上传的图片
     * @return 形如 {@code /api/files/2026/10/07/xxxx.jpg} 的相对 URL，可直接写入 {@code product.main_image}
     * @throws BizException 文件为空、超限或类型不在白名单
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的图片");
        }

        long maxBytes = properties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw BizException.badRequest("图片大小不能超过 " + properties.getMaxSizeMb() + "MB");
        }

        String extension = resolveExtension(file.getContentType());
        String datePath = LocalDate.now().format(DATE_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

        Path targetDir = rootDir.resolve(datePath);
        Path target = targetDir.resolve(fileName);

        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("图片保存失败：{}", target, ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片保存失败，请稍后再试");
        }

        String url = properties.getUrlPrefix() + "/" + datePath + "/" + fileName;
        log.debug("图片已保存：{} -> {}", target, url);
        return url;
    }

    /** 落盘根目录（绝对路径），供静态资源映射复用。 */
    public Path getRootDir() {
        return rootDir;
    }

    /** 由 Content-Type 推导扩展名，非白名单类型直接拒绝。 */
    private String resolveExtension(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        String extension = ALLOWED_TYPES.get(normalized);
        if (!StringUtils.hasText(extension)) {
            throw BizException.badRequest("仅支持 JPG / PNG / GIF / WEBP 格式的图片");
        }
        return extension;
    }
}
