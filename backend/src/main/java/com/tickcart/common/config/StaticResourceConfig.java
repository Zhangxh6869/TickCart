package com.tickcart.common.config;

import com.tickcart.common.file.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 上传图片的静态资源映射：把 {@code tickcart.upload.url-prefix} 暴露为落盘目录的只读访问入口。
 *
 * <p>映射前缀必须与 {@link FileStorageService#store} 拼出的 URL 一致，两者改动需同步。
 * 该路径在 {@code SecurityConfig} 中放行 GET（{@code <img src>} 不会带 Authorization 头）。</p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final UploadProperties properties;
    private final FileStorageService fileStorageService;

    public StaticResourceConfig(UploadProperties properties, FileStorageService fileStorageService) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = properties.getUrlPrefix() + "/**";
        String location = fileStorageService.getRootDir().toUri().toString();
        // Spring 要求 location 以 / 结尾，否则会被当成文件而非目录
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        log.info("上传图片静态映射：{} -> {}", pattern, location);
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }
}
