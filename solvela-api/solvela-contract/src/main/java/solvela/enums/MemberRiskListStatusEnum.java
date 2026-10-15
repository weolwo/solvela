package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控名单条目的状态。取值与 {@code t_member_risk_list.status} 一致。
 *
 * <h3>撤销不删行</h3>
 * 解除一条黑名单时把状态改成 {@link #REVOKED} 并记下撤销人、原因、时间 ——
 * 「谁加的、谁解的、为什么」都要查得到。删行的话，下一个人看到的是「这个 IP 从来没被封过」。
 *
 * <h3>为什么是枚举而不是两个 int 常量</h3>
 * 本项目有一条棘轮测试（{@code EnumMigrationRatchetTest}）盯着「状态类魔法常量」，
 * 原因写在它的断言里：列一旦枚举化，{@code 常量.equals(实体.getStatus())} 会<b>恒为 false
 * 而且编译不报错</b> —— 那种 bug 查起来极其费劲。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskListStatusEnum implements BaseEnum {

    /** 已撤销。到期失效<b>不</b>改这一列（见 {@code RiskListCache}：到期在读的时候判） */
    REVOKED(0, "已撤销"),

    VALID(1, "有效"),
    ;

    private final Integer value;

    private final String desc;
}
