package solvela.admin.module.member.risk.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;

import java.time.LocalDateTime;

/**
 * 决策流水的一行。
 *
 * <h3>🔴 不下发明文手机号 / 邮箱</h3>
 * 这个页面会被截图、会进浏览器缓存。{@code identityHashHex} 是摘要，
 * 它够用来「把同一个身份的多次尝试串起来」，而那正是这一列存在的理由。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskEventVO {

    private Long id;

    @Schema(description = "事件：1-登录 2-登录二次验证")
    private MemberRiskEventTypeEnum eventType;

    @Schema(description = "二次验证指回签票那次登录的 id")
    private Long refEventId;

    @Schema(description = "会员号。查无此人时为空")
    private Long memberId;

    @Schema(description = "身份摘要（不是明文）。查无此人的尝试靠它聚合")
    private String identityHashHex;

    private Integer loginType;

    private String deviceId;

    @Schema(description = "设备信任：0-无设备 1-新设备 2-老交情 3-验证过 4-注册设备")
    private Integer deviceTrust;

    @Schema(description = "IP 熟悉度：0-陌生 1-宽松熟悉 2-严格熟悉")
    private Integer ipFamiliar;

    @Schema(description = "是否沉睡：0-活跃 1-沉睡")
    private Integer dormant;

    private String clientIp;

    private String ipRegion;

    private Integer captchaPassed;

    private Integer preScore;

    private Integer postScore;

    @Schema(description = "陪跑总分：假如所有陪跑规则都生效")
    private Integer shadowScore;

    @Schema(description = "生效结论 —— 用户实际受到的处理")
    private MemberRiskDecisionEnum decision;

    @Schema(description = "陪跑结论 —— 假如所有规则都打开，这个人会受到的处理")
    private MemberRiskDecisionEnum shadowDecision;

    private Integer verifyChannel;

    @Schema(description = "这次尝试最后怎么样了")
    private MemberRiskOutcomeEnum outcome;

    @Schema(description = "事后标注：1-确认本人 2-确认被盗")
    private Integer label;

    private Integer labelSource;

    private LocalDateTime labelTime;

    @Schema(description = "风控自身耗时（毫秒），不含验密码")
    private Integer costMs;

    private String traceId;

    private LocalDateTime createTime;
}
