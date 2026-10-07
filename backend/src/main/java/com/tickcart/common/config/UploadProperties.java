package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片上传配置项，对应 {@code tickcart.upload.*}。
 *
 * <p>{@link #urlPrefix} 必须与 {@code StaticResourceConfig} 中注册的静态资源映射保持一致，
 * 否则 {@code FileStorageService} 返回的 URL 会 404。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.upload")
public class UploadProperties {

    /** 图片落盘根目录（相对路径以进程工作目录为基准）。 */
    private String dir = "uploads";

    /** 对外访问前缀，最终 URL 形如 {@code /api/files/2026/10/07/xxx.jpg}。 */
    private String urlPrefix = "/api/files";

    /** 单张图片大小上限（MB），同时受 {@code spring.servlet.multipart.max-file-size} 约束。 */
    private long maxSizeMb = 5;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }
}
