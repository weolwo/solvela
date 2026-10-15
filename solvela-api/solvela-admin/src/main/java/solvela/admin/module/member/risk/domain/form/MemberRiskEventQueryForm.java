package solvela.admin.module.member.risk.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDateTime;

/**
 * 风控决策流水的查询表单。
 *
 * <h3>🔴 手机号 / 邮箱收的是明文，但明文不会进域、也不会落 SQL 日志</h3>
 * 客服能说得出口的是手机号，不是 64 位摘要。所以这里收明文，
 * 由 Service 规范化之后算成 {@code PiiHasher} 摘要再查 —— 与登录那一侧算出来的必须是同一个值。
 *
 * @Date 2026-10-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberRiskEventQueryForm extends PageParam {

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    @Schema(description = "手机号或邮箱（明文）。服务端算成摘要再查 —— 查无此人的尝试只能靠它找到")
    private String identity;

    @Schema(description = "客户端 IP：精确匹配")
    private String clientIp;

    @Schema(description = "设备号：精确匹配")
    private String deviceId;

    @Schema(description = "生效结论：1-放行 2-滑块 3-OTP 4-换通道OTP 5-密码冷却 6-限流 7-拒绝")
    private Integer decision;

    @Schema(description = "最终结局：见 MemberRiskOutcomeEnum")
    private Integer outcome;

    @Schema(description = "只看「生效结论与陪跑结论不一致」的 —— 校准阈值时就看这个")
    private Boolean shadowDiffOnly;

    @Schema(description = "开始时间。不传默认最近 7 天（表按月分区，不带时间会扫所有分区）")
    private LocalDateTime startTime;

    @Schema(description = "结束时间。不传默认现在")
    private LocalDateTime endTime;
}
