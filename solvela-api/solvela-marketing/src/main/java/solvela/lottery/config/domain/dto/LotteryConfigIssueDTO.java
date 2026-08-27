package solvela.lottery.config.domain.dto;

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
public class LotteryConfigIssueDTO {

    /** 告警编码 */
    private String code;

    /** 严重级别: DANGER-上不了线或用户领不到号, WARN-需要提前知晓 */
    private String level;

    /** 人话说明：发现了什么，以及会导致什么后果 */
    private String message;

    public static LotteryConfigIssueDTO danger(String code, String message) {
        return new LotteryConfigIssueDTO(code, "DANGER", message);
    }

    public static LotteryConfigIssueDTO warn(String code, String message) {
        return new LotteryConfigIssueDTO(code, "WARN", message);
    }
}
