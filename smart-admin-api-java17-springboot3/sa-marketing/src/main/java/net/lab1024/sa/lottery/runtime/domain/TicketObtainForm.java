package net.lab1024.sa.lottery.runtime.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 领号入参（HTTP 侧）。
 *
 * Service 的方法签名用的是平铺参数而不是本对象，理由是规则脚本里
 * {@code obtainTicket(code, issueNo, member)} 比先 new 再 setter 可读得多
 * （不是因为 QLExpress 传不了对象 —— 它可以）。
 * 本 Form 只负责把 HTTP 请求体转成那几个参数，顺带用注解做一层基础校验。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Data
public class TicketObtainForm {

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "期号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "期号 不能为空")
    private String issueNo;

    @Schema(description = "会员唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员标识 不能为空")
    private String memberName;

    /**
     * 幂等键。调用方传了才启用防重 —— 这是「网络重试防重」，
     * 与业务限购是两回事（限购由上游负责，本模块允许同一用户领任意多张）
     */
    @Schema(description = "幂等请求ID：同一个 requestId 只会发出一个号码")
    private String requestId;
}
