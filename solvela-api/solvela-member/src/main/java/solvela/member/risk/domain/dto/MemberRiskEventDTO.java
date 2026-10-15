package solvela.member.risk.domain.dto;

import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;

import java.time.LocalDateTime;

/**
 * 一行决策流水，给后台看。
 *
 * <p>🔴 {@code identityHashHex} 下发的是摘要，<b>不是</b>明文手机号 / 邮箱 ——
 * 这个页面会被截图、会进浏览器缓存，而 PII 不该从这里漏出去。
 * 客服要按手机号查就在筛选框里输明文，接入层算成摘要再查。
 *
 * @Date 2026-10-16
 */
@Data
public class MemberRiskEventDTO {

    private Long id;

    private MemberRiskEventTypeEnum eventType;

    private Long refEventId;

    private Long memberId;

    private String identityHashHex;

    private Integer loginType;

    private String deviceId;

    /** 0-无设备, 1-新设备, 2-老交情, 3-验证过, 4-注册设备 */
    private Integer deviceTrust;

    /** 0-陌生, 1-宽松熟悉, 2-严格熟悉 */
    private Integer ipFamiliar;

    private Integer dormant;

    private String clientIp;

    private String ipRegion;

    private Integer captchaPassed;

    private Integer preScore;

    private Integer postScore;

    private Integer shadowScore;

    private MemberRiskDecisionEnum decision;

    private MemberRiskDecisionEnum shadowDecision;

    private Integer verifyChannel;

    private MemberRiskOutcomeEnum outcome;

    private Integer label;

    private Integer labelSource;

    private LocalDateTime labelTime;

    private Integer costMs;

    private String traceId;

    private LocalDateTime createTime;
}
