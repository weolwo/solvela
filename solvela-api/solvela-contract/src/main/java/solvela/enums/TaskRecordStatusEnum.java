package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务记录状态，对齐 {@code t_task_record.status}。
 *
 * <h3>取值是有序的</h3>
 * {@link #RUNNING} &lt; {@link #COMPLETED} &lt; {@link #DISPATCHED}，
 * 业务上确实存在「至少达到某一档」的判断（例如验收用例里的
 * {@code status >= COMPLETED} 表示「达标了，发没发奖不管」）。
 * 换成枚举后这类比较用 {@link #atLeast(TaskRecordStatusEnum)}，
 * 而不是拿 {@code ordinal()} 去比 —— ordinal 依赖声明顺序，
 * 有人调换一下常量位置就会静默改变语义。
 *
 * <p>{@link #EXPIRED} 不在这条链上：它是时间到了的旁路终态，与达标程度无关，
 * 所以 {@code atLeast} 对它没有意义。
 *
 * <h3>🔴 状态机的规则在这里，闸门在 SQL 里</h3>
 * {@link #canTransitTo} 是唯一一份「谁能变成谁」的声明；真正执行迁移的是
 * {@code TaskRecordMapper.xml} 里带旧状态条件的 UPDATE（达标 / 发奖 / 过期 / 管理端禁用），
 * 不要改成先读出实体、在内存里判断再写回 —— 两个事件同时到，会各发一次奖。
 * 那几条 SQL 里的状态值引用本枚举，不写数字；{@code TaskRecordStatusSqlTest}
 * 渲染出真正发给数据库的 SQL，断言 DAO 的迁移与 {@link #canTransitTo} 一致。
 * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum TaskRecordStatusEnum implements BaseEnum {

    /**
     * 进行中（含「低档已发奖、最高档未达标」）
     */
    RUNNING(0, "进行中"),

    /**
     * 已完成 = 最高档达标
     */
    COMPLETED(1, "已完成"),

    /**
     * 已发奖 = 最高档的奖也发完了，此后不再接受事件
     */
    DISPATCHED(2, "已发奖"),

    /**
     * 已过期：时间到了的旁路终态，不在达标链上
     */
    EXPIRED(3, "已过期"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 是否至少达到了 {@code other} 这一档。
     *
     * <p>刻意基于 {@code value} 而不是 {@code ordinal()}：ordinal 依赖常量声明顺序，
     * 调换位置就会静默改变语义，而 value 是落库的、不可能被随手改。
     */
    public boolean atLeast(TaskRecordStatusEnum other) {
        return this.value >= other.value;
    }

    /**
     * 本状态能否迁到 {@code to}。已发奖、已过期都是终态。
     *
     * <p>⚠️ 已完成(1) 不能被禁用成已过期：达标了就是达标了，过期任务与管理端禁用都只认进行中。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(TaskRecordStatusEnum to) {
        return switch (this) {
            case RUNNING -> to == COMPLETED || to == EXPIRED;
            case COMPLETED -> to == DISPATCHED;
            case DISPATCHED, EXPIRED -> false;
        };
    }
}
