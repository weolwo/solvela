package sa.lottery.issue.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 期号配置 更新表单
 *
 * ⚠️ <b>只允许改时间</b>。生成器原本开放的 settleTime / winningNumber / status 已全部移除，
 * lotteryCode 与 issueNo 也刻意不在此列：
 * <ul>
 *   <li>issueNo 是 FPE 的 tweak（key = HMAC(secret, lotteryCode|issueNo)），
 *       改了等于换密钥 —— 已发号码反解失效、签名全废、新号码还可能与历史重复；</li>
 *   <li>winningNumber / settleTime 只能由开奖流程写入，让前端能改等于「自己填中奖号码」；</li>
 *   <li>status 由状态机流转，手工改会绕过开奖的并发闸门。</li>
 * </ul>
 * 这里直接不定义这些字段，而不是在 Service 里忽略 —— 让越权意图在编译期就无处安放。
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@Data
public class LotteryIssueUpdateForm {

    @Schema(description = "期号ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "售卖开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖开始时间 不能为空")
    private LocalDateTime saleStartTime;

    @Schema(description = "售卖结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖结束时间 不能为空")
    private LocalDateTime saleEndTime;

    @Schema(description = "计划开奖时间")
    private LocalDateTime planDrawTime;
}
