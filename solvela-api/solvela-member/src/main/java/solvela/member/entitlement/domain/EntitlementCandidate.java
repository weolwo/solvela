package solvela.member.entitlement.domain;

import lombok.Data;

/**
 * 一个「该拿到这份权益」的候选会员：会员号 + 他<b>此刻</b>的等级。
 *
 * <p>等级一起查出来，不是拿到 memberId 之后再逐个查 ——
 * 一次扫描几百上千人，逐个查就是几百上千次往返，而那个数在同一次扫描里不会变。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Data
public class EntitlementCandidate {

    private Long memberId;

    /** 当前等级。没有成长值行的会员是 0 —— 他确实还没攒过任何成长值 */
    private Integer gradeCode;
}
