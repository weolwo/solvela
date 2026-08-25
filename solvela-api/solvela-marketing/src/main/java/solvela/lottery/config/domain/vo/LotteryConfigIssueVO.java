package solvela.lottery.config.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条彩票玩法体检告警。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
@AllArgsConstructor
public class LotteryConfigIssueVO {

    @Schema(description = "告警编码")
    private String code;

    @Schema(description = "严重级别: DANGER-上不了线或用户领不到号, WARN-需要提前知晓")
    private String level;

    @Schema(description = "人话说明：发现了什么，以及会导致什么后果")
    private String message;

    public static LotteryConfigIssueVO danger(String code, String message) {
        return new LotteryConfigIssueVO(code, "DANGER", message);
    }

    public static LotteryConfigIssueVO warn(String code, String message) {
        return new LotteryConfigIssueVO(code, "WARN", message);
    }
}
