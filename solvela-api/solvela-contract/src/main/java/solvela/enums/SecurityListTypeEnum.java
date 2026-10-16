package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 安全名单类型。取值与 {@code t_security_list.list_type} 一致。
 *
 * <p>同一个值同时在黑、白名单里时<b>黑名单优先</b>。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum SecurityListTypeEnum implements BaseEnum {

    BLACK(1, "黑名单"),

    /** 命中加条目上的分 */
    GREY(2, "灰名单"),

    /** 豁免面刻意窄：白名单上的东西恰恰是攻击者最想拿到的（方案 §5.2） */
    WHITE(3, "白名单"),
    ;

    private final Integer value;

    private final String desc;
}
