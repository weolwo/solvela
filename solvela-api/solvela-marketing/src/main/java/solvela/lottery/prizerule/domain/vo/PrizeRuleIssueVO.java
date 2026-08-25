package solvela.lottery.prizerule.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一条体检告警。
 *
 * <p>只描述「发现了什么、为什么这是问题」，<b>不给出修复动作</b> ——
 * 奖级配置是资损敏感数据，一键改错的代价远大于让人多点两下。
 * 页面拿到它只负责标红并给出跳工作台的入口。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
@AllArgsConstructor
public class PrizeRuleIssueVO {

    @Schema(description = "告警编码，供前端做图标/分组，不直接展示")
    private String code;

    @Schema(description = "严重级别: DANGER-会导致开奖或派奖出错, WARN-配置可疑但能跑")
    private String level;

    @Schema(description = "人话说明：发现了什么，以及为什么这是问题")
    private String message;

    public static PrizeRuleIssueVO danger(String code, String message) {
        return new PrizeRuleIssueVO(code, "DANGER", message);
    }

    public static PrizeRuleIssueVO warn(String code, String message) {
        return new PrizeRuleIssueVO(code, "WARN", message);
    }
}
