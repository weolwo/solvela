package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员实名认证状态，对齐 {@code t_member_verify.verify_status}。
 *
 * <p>⚠️ <b>只有 {@link #PENDING} 能被审核</b>：审核接口先按这个状态捞记录，捞不到就说明
 * 这条已经被别人处理过了。这既是幂等闸门也是并发闸门 —— 两个运营同时点通过时，
 * 第二个会因为状态已变而拿不到记录。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum MemberVerifyStatusEnum implements BaseEnum {

    NONE(0, "未认证"),

    /**
     * 认证中：唯一可被审核的状态
     */
    PENDING(1, "认证中"),

    VERIFIED(2, "已认证"),

    FAILED(3, "认证失败"),
    ;

    private final Integer value;

    private final String desc;
}
