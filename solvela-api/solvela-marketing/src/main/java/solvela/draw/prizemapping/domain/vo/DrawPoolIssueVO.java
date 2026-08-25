package solvela.draw.prizemapping.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条奖池体检告警。
 *
 * <p>只描述「发现了什么、会导致什么后果」，不给修复动作 ——
 * 奖池概率与库存是资损敏感数据，一键改错的代价远大于让人多点两下。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
@AllArgsConstructor
public class DrawPoolIssueVO {

    @Schema(description = "告警编码，供前端分组，不直接展示")
    private String code;

    @Schema(description = "严重级别: DANGER-会导致抽奖报错或发不出奖, WARN-配置可疑但能跑")
    private String level;

    @Schema(description = "人话说明：发现了什么，以及会导致什么后果")
    private String message;

    public static DrawPoolIssueVO danger(String code, String message) {
        return new DrawPoolIssueVO(code, "DANGER", message);
    }

    public static DrawPoolIssueVO warn(String code, String message) {
        return new DrawPoolIssueVO(code, "WARN", message);
    }
}
