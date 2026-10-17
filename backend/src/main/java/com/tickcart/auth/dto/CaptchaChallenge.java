package com.tickcart.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 人机验证挑战：图形码标识 + 可直接内联渲染的 SVG。
 *
 * <p>用 SVG 而不是图片 URL：前端 {@code v-html} 一行就能显示，不用额外请求、
 * 不用处理图片缓存，也不需要引入 Canvas 依赖。</p>
 *
 * @param id        图形码标识，提交登录时回传
 * @param svg       图形码 SVG 源码
 * @param expiresIn 有效秒数
 */
@Schema(description = "人机验证挑战")
public record CaptchaChallenge(String id, String svg, int expiresIn) {
}
