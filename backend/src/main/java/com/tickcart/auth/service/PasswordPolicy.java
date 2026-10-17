package com.tickcart.auth.service;

import com.tickcart.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 密码强度策略（仅注册侧使用）。
 *
 * <p>分两层：长度与字符组合交给 Bean Validation（声明在 DTO 上，错误信息可被前端直接展示）；
 * 本类负责「规则表达不了」的部分 —— 弱口令字典与「密码包含用户名」。</p>
 *
 * <p><b>为什么不加在登录侧：</b>登录是「验证已有密码」，不是「设置新密码」。
 * 在登录侧加复杂度校验会让历史账号与演示账号（demo/123456）直接登不进来。</p>
 */
@Component
public class PasswordPolicy {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);
    private static final String DICTIONARY = "security/weak-passwords.txt";

    /** 常见弱口令，小写存储；字典缺失时退化为空集合（不阻断注册，只少一道检查）。 */
    private final Set<String> weakPasswords;

    public PasswordPolicy() {
        this.weakPasswords = loadDictionary();
    }

    /**
     * 校验密码是否可接受。
     *
     * @param rawPassword 明文密码
     * @param username    用户名，用于「密码不得包含用户名」检查
     * @throws BizException 命中弱口令或包含用户名
     */
    public void validatePassword(String rawPassword, String username) {
        if (!StringUtils.hasText(rawPassword)) {
            return; // 空值由 @NotBlank 负责，这里不重复报错
        }
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (weakPasswords.contains(lower)) {
            throw BizException.badRequest("密码过于简单，请更换一个");
        }
        if (StringUtils.hasText(username)
                && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw BizException.badRequest("密码不能包含用户名");
        }
    }

    private Set<String> loadDictionary() {
        Set<String> set = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(DICTIONARY);
        if (!resource.exists()) {
            log.warn("弱口令字典 {} 不存在，弱口令检查将跳过", DICTIONARY);
            return Collections.emptySet();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    set.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ex) {
            log.warn("弱口令字典加载失败，弱口令检查将跳过：{}", ex.getMessage());
            return Collections.emptySet();
        }
        log.info("弱口令字典加载完成，共 {} 条", set.size());
        return Collections.unmodifiableSet(set);
    }
}
