package solvela.admin.module.lottery.issue.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 期号配置 新建表单
 *
 * ⚠️ 生成器原本还开放了 soldCount / settleTime / winningNumber / status 四个字段，
 * 已全部移除 —— 它们都是<b>由系统在运行中产生</b>的：
 * soldCount 由领号累加、winningNumber 与 settleTime 由开奖写入、status 由状态机流转。
 * 让调用方能填这些，等于把「已售 0 张」「开奖号码是 88888」直接交给前端决定。
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@Data
public class LotteryIssueAddForm {

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    /**
     * ⚠️ 期号同时是 FPE 的 tweak（key = HMAC(secret, lotteryCode|issueNo)），
     * 创建后不可修改 —— 改了等于换密钥，已发号码全部失效。UpdateForm 里刻意不含此字段。
     *
     * 它不走「10 位随机码」的业务编码规范：期号是给运营和用户看的可读标识（如 2026_MID_01），
     * 唯一性由 uk_issue_no(lottery_code, issue_no) 在玩法内保证即可，不需要全局唯一。
     */
    @Schema(description = "期号，如 2026_MID_01。创建后不可修改", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "期号 不能为空")
    private String issueNo;

    @Schema(description = "售卖开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖开始时间 不能为空")
    private LocalDateTime saleStartTime;

    @Schema(description = "售卖结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖结束时间 不能为空")
    private LocalDateTime saleEndTime;

    @Schema(description = "计划开奖时间：对外承诺的开奖时刻，与实际执行的 settleTime 是两回事")
    private LocalDateTime planDrawTime;
}
