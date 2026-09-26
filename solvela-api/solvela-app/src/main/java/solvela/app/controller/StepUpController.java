package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentDevice;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.StepUpCodeView;
import solvela.app.domain.StepUpVerifyRequest;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.ClientIp;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpFailReason;
import solvela.member.api.StepUpResult;

/**
 * 敏感操作二次验证。客户端收到 {@code STEP_UP_REQUIRED} 后走这两步，然后<b>重试原请求</b>。
 *
 * <pre>
 *   POST /address                → 403 STEP_UP_REQUIRED
 *   POST /auth/step-up/code      → {"maskedEmail": "a***@x.com"}
 *   POST /auth/step-up/verify    → 204，这台设备从此受信任
 *   POST /address（重试）         → 200
 * </pre>
 *
 * <p>两个接口都<b>要登录</b>，且都不带 {@code @StepUpRequired}（那会成为死循环）。
 *
 * @Date 2026-09-26
 */
@Tag(name = "二次验证")
@RestController
@RequestMapping("/auth/step-up")
@RequiredArgsConstructor
public class StepUpController {

    private final MemberStepUpApi stepUpApi;

    /** 发一封验证码到当前会员已绑定的邮箱。 */
    @PostMapping("/code")
    public StepUpCodeView sendCode(HttpServletRequest servletRequest) {
        StepUpResult result = stepUpApi.sendCode(cmd(servletRequest, null));
        if (!result.success()) {
            throw translate(result);
        }
        return new StepUpCodeView(result.maskedEmail());
    }

    /**
     * 校验验证码。通过即把当前设备记为受信任，返回 204。
     */
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@RequestBody @Valid StepUpVerifyRequest request,
                                       HttpServletRequest servletRequest) {
        StepUpResult result = stepUpApi.verify(cmd(servletRequest, request.code()));
        if (!result.success()) {
            throw translate(result);
        }
        return ResponseEntity.noContent().build();
    }

    private static StepUpCmd cmd(HttpServletRequest servletRequest, String code) {
        return new StepUpCmd(CurrentMember.require().memberId(), CurrentDevice.deviceIdOrNull(),
                ClientIp.of(servletRequest), code);
    }

    /**
     * 失败原因 → HTTP 契约。switch 表达式：新增原因时编译不过。
     *
     * <p>验证码错用 {@code BAD_CREDENTIALS}（401）：与绑定邮箱那几条同一个码、同一套客户端处理。
     * 客户端只有 {@code LOGIN_REQUIRED} 才清会话，所以这里的 401 不会把用户踢出去。
     */
    private static ApiException translate(StepUpResult result) {
        return switch (result.reason()) {
            case NO_DEVICE -> new ApiException(ApiErrors.DEVICE_REQUIRED);
            case NO_EMAIL -> new ApiException(ApiErrors.CONFLICT, "请先绑定邮箱，再进行该操作");
            case TOO_FREQUENT -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    String.format("验证码已发送，请 %d 秒后再试", Math.max(1, result.retryAfterSeconds())));
            case DAILY_LIMIT_REACHED -> new ApiException(ApiErrors.OPERATION_LIMITED,
                    "今日验证码发送次数已用完，请明天再试");
            // 我们自己的问题，如实说「稍后再试」
            case SEND_FAILED -> new ApiException(ApiErrors.INTERNAL, "验证码发送失败，请稍后再试");
            case CODE_EXPIRED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码已失效，请重新获取");
            case CODE_MISMATCH -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误");
            case CODE_LOCKED -> new ApiException(ApiErrors.BAD_CREDENTIALS, "验证码错误次数过多，请重新获取");
        };
    }
}
