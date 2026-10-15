package solvela.admin.module.member.risk.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 改一条风控规则。
 *
 * <h3>🔴 只有这三样能改</h3>
 * 编码、阶段、类型都是<b>代码</b>决定的 —— 让后台改它们等于让人在页面上改代码，
 * 而改错的表现是启动失败（阶段与代码不一致）或者规则静默失效。
 * 规则的逻辑在 Java 类里，这里能调的只是「松紧」。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskRuleUpdateForm {

    @Schema(description = "规则 id")
    @NotNull(message = "规则 id 不能为空")
    private Long id;

    @Schema(description = "档位：0-关闭 1-陪跑（只记录）2-生效")
    @NotNull(message = "档位不能为空")
    @Min(value = 0, message = "档位只能是 0 / 1 / 2")
    @Max(value = 2, message = "档位只能是 0 / 1 / 2")
    private Integer mode;

    /**
     * 打分规则命中加的分；强规则填 0。
     *
     * <p>上限 1000 不是业务约束，是列宽约束（{@code smallint}）加一道常识 ——
     * 分档线最高 80，填一个四位数只可能是手滑。
     */
    @Schema(description = "命中加的分（打分规则用，强规则填 0）")
    @NotNull(message = "分值不能为空")
    @Min(value = 0, message = "分值不能是负数")
    @Max(value = 1000, message = "分值最多 1000 —— 分档线最高 80，填更大的数只可能是手滑")
    private Integer score;

    @Schema(description = "参数 JSON。不传表示不改；传空串表示清空（回到代码里的默认值）")
    private String params;
}
