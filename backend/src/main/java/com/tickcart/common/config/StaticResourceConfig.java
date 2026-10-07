package com.tickcart.common.config;

import com.tickcart.common.file.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 静态资源映射，把磁盘目录暴露为只读 HTTP 访问入口：
 *
 * <ul>
 *   <li>{@code tickcart.upload.url-prefix}/**（默认 {@code /api/files/**}）→ 后台上传目录</li>
 *   <li>{@code /img/**} → 演示图片目录 {@code tickcart.static.imgs-dir}（仓库内 {@code imgs/}）</li>
 * </ul>
 *
 * <p>两个前缀都必须与数据库里存的 URL 前缀一致（上传见
 * {@link FileStorageService#store}），并在 {@code SecurityConfig} 中放行 GET
 * —— {@code <img src>} 不会携带 Authorization 头。</p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final UploadProperties uploadProperties;
    private final StaticProperties staticProperties;
    private final FileStorageService fileStorageService;

    public StaticResourceConfig(UploadProperties uploadProperties,
                                StaticProperties staticProperties,
                                FileStorageService fileStorageService) {
        this.uploadProperties = uploadProperties;
        this.staticProperties = staticProperties;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        register(registry, uploadProperties.getUrlPrefix() + "/**", fileStorageService.getRootDir());
        register(registry, "/img/**", Path.of(staticProperties.getImgsDir()).toAbsolutePath().normalize());
    }

    /** 注册单个映射；location 必须以 {@code /} 结尾，否则 Spring 会当成文件而非目录。 */
    private void register(ResourceHandlerRegistry registry, String pattern, Path dir) {
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        log.info("静态资源映射：{} -> {}", pattern, location);
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }
}
