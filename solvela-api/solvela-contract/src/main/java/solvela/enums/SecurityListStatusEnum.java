package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 安全名单条目的状态。取值与 {@code t_security_list.status} 一致。
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
public enum SecurityListStatusEnum implements BaseEnum {

    /** 已撤销。到期失效<b>不</b>改这一列（见 {@code RiskListCache}：到期在读的时候判） */
    REVOKED(0, "已撤销"),

    VALID(1, "有效"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}：有效 ↔ 已撤销。
     *
     * <p>🔴 每条边的 UPDATE 都带旧状态条件：
     * <ul>
     *   <li>有效 → 已撤销：后台撤销 {@code SecurityListDao#revokeValid}、黑到期转灰 {@code SecurityListMaintainJob#escalateOne}；</li>
     *   <li>已撤销（或有效但已过期）→ 有效：自动写入复用旧行 {@code SecurityListWriter#reuse}，条件是「仍不是有效的」，
     *       这样读完到写之间被人工重新加上的有效条目不会被自动条目覆盖。</li>
     * </ul>
     * 后者与转灰是 JdbcTemplate 写的 SQL，状态值绑的是本枚举的取值，不是字面量。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(SecurityListStatusEnum to) {
        return switch (this) {
            case VALID -> to == REVOKED;
            case REVOKED -> to == VALID;
        };
    }
}
