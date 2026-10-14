package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 风控规则所在的决策点。
 *
 * <h3>🔴 两个阶段能用的信息不一样</h3>
 * {@link #PRE} 在验密码之前：可以查账号，但账号信息<b>只能用来放宽</b>结论 ——
 * 否则「有号 / 没号」会得到不同的回答，那是账号枚举的口子（方案 §3.1）。
 * {@link #POST} 在密码验对之后，账号信号随便用。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum MemberRiskPhaseEnum implements BaseEnum {

    PRE(1, "前置"),

    POST(2, "后置"),
    ;

    private final Integer value;

    private final String desc;
}
