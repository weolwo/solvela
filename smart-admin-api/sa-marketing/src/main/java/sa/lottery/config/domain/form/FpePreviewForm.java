package sa.lottery.config.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * FPE 算号推演台入参
 *
 * ⚠️ lotteryCode 必传：密钥是 HMAC(masterSecret, lotteryCode + "|" + issueNo)，
 * 缺了它服务端根本算不出正确的 key，推演结果会和线上真实发出的号码对不上 ——
 * 一个「看起来能用、实际全错」的预览比没有预览更糟。
 *
 * ⚠️ 刻意不含 totalCount：它不是密码学输入，只用于前端把游标输入框的 :max 限住，
 * 放进请求体会让人误以为它参与算号。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class FpePreviewForm {

    @Schema(description = "彩票编码（参与密钥派生，必传）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "号码长度，取值 4~9", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "号码长度 不能为空")
    @Min(value = 4, message = "号码长度不能小于 4 位")
    @Max(value = 9, message = "号码长度不能大于 9 位")
    private Integer numberLength;

    @Schema(description = "推演的游标（1-indexed，与运营心智一致：第 1 个领号的人）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "游标 不能为空")
    @Min(value = 1, message = "游标从 1 开始")
    private Long sequenceNo;
}
