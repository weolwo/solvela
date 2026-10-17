package solvela.app.controller;

import solvela.app.ratelimit.RateLimit;
import solvela.app.ratelimit.RateLimitScene;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.captcha.CaptchaService;
import solvela.app.captcha.TrackChecker;
import solvela.app.domain.InputLimits;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;

/**
 * 滑块验证码。客户端在受保护接口回 {@code CAPTCHA_REQUIRED} 时来这里：取一张图 → 拖 → 拿通行票 → 带着票重试原请求。
 *
 * <p>两个接口都 {@link Anonymous}：它们保护的正是登录、发码这些匿名接口。
 * 设备令牌照常要求（没有 {@code @DeviceExempt}）—— 取图的也该是一台我们认得的设备。
 *
 * @Date 2026-09-27
 */
@Tag(name = "滑块验证码")
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /** 取一张图。答案不下发 */
    @Anonymous
    @RateLimit(RateLimitScene.CAPTCHA)
    @PostMapping
    public CaptchaService.Challenge create() {
        return captchaService.create();
    }

    /**
     * 判题。拖对了返回通行票，拖错返回 400 {@code CAPTCHA_FAILED}（客户端换一张图重来）。
     *
     * @param body {@code {"captchaId": "...", "x": 123, "track": [[t, x, y], ...]}}，
     *             x 是拼图左边缘在<b>原图坐标</b>里的位置，track 是拖动轨迹（见 {@link solvela.app.captcha.TrackChecker}）
     */
    @Anonymous
    @RateLimit(RateLimitScene.CAPTCHA)
    @PostMapping("/verify")
    public PassView verify(@RequestBody @Valid VerifyRequest body) {
        String pass = captchaService.verify(body.captchaId(), body.x(), body.track());
        if (pass == null) {
            throw new ApiException(ApiErrors.CAPTCHA_FAILED);
        }
        return new PassView(pass);
    }

    public record VerifyRequest(
            @Size(max = InputLimits.TOKEN_LENGTH, message = "验证已过期，请换一张") String captchaId,
            Integer x,
            // 轨迹点数的业务判定在 TrackChecker（超了算「不像人」）；这里是天花板，与它同一个数
            @Size(max = TrackChecker.MAX_POINTS, message = "验证已过期，请换一张") int[][] track) {
    }

    /** @param captchaToken 通行票：放进请求头 {@code X-Captcha-Token} 重试原请求，用一次作废 */
    public record PassView(String captchaToken) {
    }
}
