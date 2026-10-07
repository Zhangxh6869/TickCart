package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 静态资源目录配置，对应 {@code tickcart.static.*}。
 *
 * <p>与 {@link UploadProperties} 分开：这里是**随仓库提交**的演示素材目录，
 * 上传目录则是运行时可写的产物目录。</p>
 */
@Component
@ConfigurationProperties(prefix = "tickcart.static")
public class StaticProperties {

    /**
     * 演示图片目录，以 {@code /img/**} 对外提供访问。
     *
     * <p>默认 {@code ../imgs}：后端以 {@code backend/} 为工作目录启动时，
     * 正好指向仓库根目录的 {@code imgs/}。</p>
     */
    private String imgsDir = "../imgs";

    public String getImgsDir() {
        return imgsDir;
    }

    public void setImgsDir(String imgsDir) {
        this.imgsDir = imgsDir;
    }
}
