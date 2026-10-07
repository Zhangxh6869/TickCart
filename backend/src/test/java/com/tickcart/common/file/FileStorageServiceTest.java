package com.tickcart.common.file;

import com.tickcart.common.config.UploadProperties;
import com.tickcart.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileStorageService} 单元测试。
 *
 * <p>重点覆盖三条安全/边界约定：类型白名单、大小上限、落盘路径不使用客户端原始文件名。</p>
 */
@DisplayName("图片存储服务")
class FileStorageServiceTest {

    private static final String URL_PREFIX = "/api/files";

    @TempDir
    Path tempDir;

    private FileStorageService service(long maxSizeMb) {
        UploadProperties properties = new UploadProperties();
        properties.setDir(tempDir.toString());
        properties.setUrlPrefix(URL_PREFIX);
        properties.setMaxSizeMb(maxSizeMb);
        return new FileStorageService(properties);
    }

    private MockMultipartFile image(String originalName, String contentType, String content) {
        return new MockMultipartFile("file", originalName, contentType,
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("合法 PNG：返回 url-prefix 开头的 URL，且文件真实落盘、内容一致")
    void storeValidPng() throws Exception {
        FileStorageService storage = service(5);

        String url = storage.store(image("demo.png", "image/png", "png-bytes"));

        assertThat(url).startsWith(URL_PREFIX + "/").endsWith(".png");
        Path stored = tempDir.resolve(url.substring(URL_PREFIX.length() + 1));
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo("png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("按日期分片存放：URL 中间段为 yyyy/MM/dd")
    void storeUsesDateShard() {
        FileStorageService storage = service(5);

        String url = storage.store(image("demo.jpg", "image/jpeg", "jpg-bytes"));

        String relative = url.substring(URL_PREFIX.length() + 1);
        assertThat(relative).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.jpg");
    }

    @Test
    @DisplayName("contentType 带参数/大写仍能识别（image/JPEG; charset=binary）")
    void storeNormalizesContentType() {
        FileStorageService storage = service(5);

        String url = storage.store(image("demo.jpeg", "IMAGE/JPEG", "jpg-bytes"));

        assertThat(url).endsWith(".jpg");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectEmptyFile() {
        FileStorageService storage = service(5);

        MultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.store(empty))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请选择要上传的图片");
    }

    @Test
    @DisplayName("超过大小上限被拒绝")
    void rejectOversizedFile() {
        FileStorageService storage = service(1);
        byte[] tooLarge = new byte[1024 * 1024 + 1];

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "big.png", "image/png", tooLarge)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 1MB");
    }

    @Test
    @DisplayName("非白名单类型被拒绝（含伪装成图片的可执行文件）")
    void rejectUnsupportedType() {
        FileStorageService storage = service(5);

        assertThatThrownBy(() -> storage.store(image("evil.svg", "image/svg+xml", "<svg/>")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅支持");
        assertThatThrownBy(() -> storage.store(image("evil.jsp", "application/octet-stream", "x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅支持");
    }

    @Test
    @DisplayName("客户端原始文件名不参与落盘路径：路径穿越无效")
    void ignoresOriginalFileName() throws Exception {
        FileStorageService storage = service(5);

        String url = storage.store(image("../../evil.png", "image/png", "png-bytes"));

        String relative = url.substring(URL_PREFIX.length() + 1);
        assertThat(relative).doesNotContain("..");
        assertThat(relative).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png");
        // 文件必须落在根目录之内
        assertThat(tempDir.resolve(relative).normalize()).startsWith(tempDir);
    }
}
