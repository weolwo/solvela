package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务配置状态，对齐 {@code t_task_config.status}。
 *
 * <h3>🔴 运行态的订阅判据是「!= OFFLINE」，不是「== ACTIVE」</h3>
 * 全工程<b>没有任何地方</b>把 status 从 {@link #PENDING} 改成 {@link #ACTIVE} ——
 * {@code TaskConfigService.wizardSubmit} 落的就是 PENDING，也不存在「启用」接口。
 * 若判 {@code == ACTIVE}，所有任务永远不会被触发，而链路看起来完全正常
 * （事件收到了、日志也打了、就是一条进度都不涨）。
 *
 * <p>另外 DDL 的默认值是 0，而 0 不在本枚举里 —— 判 {@code != OFFLINE} 同时把它当作可用，
 * 与「活动有没有开始由业务层按起止时间实时算、不是后台开关」的既定口径一致。
 * ⚠️ 这意味着<b>库里若真出现 status=0 的行，读出来会抛异常</b>。
 * 现存 11 行全是 1，DDL 那个默认值属于历史遗留，建议后续改成 1。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum TaskConfigStatusEnum implements BaseEnum {

    /**
     * 待生效：向导提交后的落地状态，也是运行态实际认可的可用状态
     */
    PENDING(1, "待生效"),

    /**
     * 生效中。<b>目前没有任何代码会写入这个值</b>，保留是为了兼容存量与将来的启用开关
     */
    ACTIVE(2, "生效中"),

    /**
     * 已下线：运行态唯一的排除条件
     */
    OFFLINE(3, "已下线"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}：待生效 ↔ 已下线（经确认允许下线后再上线）。
     * 生效中没有代码会写入，只留一条到已下线的边，存量里若真有这种行也能下线。
     *
     * <p>🔴 闸门在 SQL 里：{@code TaskConfigMapper.xml} 的 {@code markPending} / {@code markOffline} 带旧状态条件。
     * {@code TaskConfigStatusSqlTest} 断言那些 SQL 与这里一条不多、一条不少。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(TaskConfigStatusEnum to) {
        return switch (this) {
            case PENDING -> to == OFFLINE;
            case ACTIVE -> to == OFFLINE;
            case OFFLINE -> to == PENDING;
        };
    }
}
