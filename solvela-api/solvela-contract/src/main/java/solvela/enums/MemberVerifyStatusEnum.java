package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员实名认证状态，对齐 {@code t_member_verify.verify_status}。
 *
 * <p>⚠️ <b>只有 {@link #PENDING} 能被审核</b>：审核的两条 UPDATE（{@code MemberVerifyMapper.xml} 的
 * {@code markVerified} / {@code markRejected}）把「仍是认证中」压进 WHERE，这既是幂等闸门也是并发闸门 ——
 * 两个运营同时点，第二个影响 0 行。以前这里写着同样的承诺，实现却是先读后 updateById，
 * 两个人同时点都会成功、后写的覆盖先写的（通过之后又被改成驳回）。
 *
 * <p>目前没有代码会建核验记录（C 端提交还没做），{@link #NONE} 也没有代码产生。
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

    /**
     * 本状态能否迁到 {@code to}：只有认证中能被审核成已认证或认证失败。
     * 驳回后重新提交（认证失败 → 认证中）还没有实现，做的时候在这里加边、在 Mapper 里加闸门。
     *
     * <p>{@code MemberVerifyStatusSqlTest} 断言审核的两条 UPDATE 与这里一一对应。
     * 不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(MemberVerifyStatusEnum to) {
        return switch (this) {
            case PENDING -> to == VERIFIED || to == FAILED;
            case NONE, VERIFIED, FAILED -> false;
        };
    }
}
