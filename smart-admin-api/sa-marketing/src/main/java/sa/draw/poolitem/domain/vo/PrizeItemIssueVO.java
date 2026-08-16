package sa.draw.poolitem.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条奖项库存体检告警。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
@AllArgsConstructor
public class PrizeItemIssueVO {

    @Schema(description = "告警编码")
    private String code;

    @Schema(description = "严重级别: DANGER-会导致抽奖报错或超发, WARN-需要关注")
    private String level;

    @Schema(description = "人话说明：发现了什么，以及会导致什么后果")
    private String message;

    public static PrizeItemIssueVO danger(String code, String message) {
        return new PrizeItemIssueVO(code, "DANGER", message);
    }

    public static PrizeItemIssueVO warn(String code, String message) {
        return new PrizeItemIssueVO(code, "WARN", message);
    }
}
