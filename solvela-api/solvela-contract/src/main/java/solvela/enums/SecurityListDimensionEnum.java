package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 安全名单的维度。取值与 {@code t_security_list.dimension} 一致。
 *
 * @Date 2026-10-15
 */
@Getter
@AllArgsConstructor
public enum SecurityListDimensionEnum implements BaseEnum {

    IP(1, "IP"),

    /** CIDR，如 {@code 203.0.113.0/24}、{@code 2001:db8::/48} */
    IP_RANGE(2, "IP段"),

    /** {@code PiiHasher} 摘要的十六进制，与 {@code t_member.phone_hash / email_hash} 同算法。不管号存不存在都能挂 */
    IDENTITY(3, "身份摘要"),

    MEMBER(4, "会员"),
    ;

    private final Integer value;

    private final String desc;
}
