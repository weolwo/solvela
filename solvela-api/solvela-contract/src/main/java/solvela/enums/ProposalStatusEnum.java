package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发放提案的状态机，对齐 {@code t_proposal_record.status}。
 *
 * <h3>为什么值不连号</h3>
 * 按阶段分段（审批 10 系 / 执行 30-40 / 终态 50-70 / 拦截 80），
 * 段内留空位方便以后插新状态而不动存量数据。
 *
 * <h3>流转</h3>
 * <pre>
 *   风控放行 ─┬─ 需审批 ─→ 10 待一审 ─┬─(单审)─→ 30 待执行
 *             │                        └─(双审)─→ 11 待二审 ─→ 30 待执行
 *             │                        任一环节驳回 ─→ 20 驳回（终态）
 *             └─ 免审 ───────────────────────────→ 30 待执行
 *
 *   30 待执行 ─→ 40 执行中 ─→ 50 成功 / 70 彻底失败
 *   30 待执行 ─────────────→ 70 彻底失败（抢执行权那一步自己抛了异常，钱没出去）
 *   风控拦截 ─────────────────────────────────→ 80 风控拦截（终态）
 * </pre>
 * 30 → 40 是条件更新做的并发闸门，抢不到的直接退出，保证同一提案只被执行一次。
 * 每条边对应 {@code ProposalRecordMapper.xml} 里一条带旧状态条件的 UPDATE，见 {@link #canTransitTo}。
 *
 * <h3>🔴 这个枚举存在的理由</h3>
 * 改造之前，这台十个状态的机器<b>没有任何一处列全过</b>：
 * {@code AssetDispatchEngine} 私有声明了 30/40/50/70（执行侧），
 * {@code ProposalRecordService} 私有声明了 10/11/20/30（审批侧），
 * 其中 30 两边各写了一遍，而 0/60/80 谁都没声明 —— 只散落在注释和 SQL 字面量里。
 * 要看懂完整流转得同时打开两个模块再对着 DDL 注释拼。
 *
 * <p>⚠️ {@code ProposalRecordMapper.xml} 里的状态值（闸门与统计）都写成
 * {@code ${@solvela.enums.ProposalStatusEnum@X.value}}，改取值 SQL 自动跟着变；
 * 但常量改名编译器管不到，由 {@code ProposalRecordStatusSqlTest} 渲染 SQL 兜住。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum ProposalStatusEnum implements BaseEnum {

    /**
     * 等待中。<b>正常链路不产生这个状态</b>：管理端「新建」按钮会造出来，
     * 正常发放只落 10 / 30 / 80 三种初始状态
     */
    WAITING(0, "等待中"),

    FIRST_REVIEW(10, "待一审"),

    /**
     * 待二审：只有 promotion_config.review_level 配了双审才会走到
     */
    SECOND_REVIEW(11, "待二审"),

    /**
     * 驳回：终态，一审或二审任一环节都可能落到这里
     */
    REJECTED(20, "驳回"),

    PENDING_EXECUTE(30, "待执行"),

    /**
     * 执行中：由 30 条件更新抢占而来，是并发闸门的落点
     */
    EXECUTING(40, "执行中"),

    SUCCESS(50, "成功"),

    /**
     * 部分成功：多资产提案里有的发出去了有的没有
     */
    PARTIAL_SUCCESS(60, "部分成功"),

    FAILED(70, "彻底失败"),

    /**
     * 风控拦截：终态。<b>钱没出去，是防线生效</b>。
     * 这条记录是刻意落库的 —— 它是合规审计和客诉排查的唯一证据。
     * 仅本状态下 {@code risk_code} 才有值（对齐 RiskBlockCode）
     */
    RISK_BLOCKED(80, "风控拦截"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。
     *
     * <p>🔴 闸门在 SQL 里：{@code ProposalRecordMapper.xml} 的每条改状态的 UPDATE 都带旧状态条件，
     * 按影响行数判成败。{@code ProposalRecordStatusSqlTest} 断言那些 SQL 的迁移与这里一条不多、一条不少。
     * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
     *
     * <p>等待中、部分成功没有任何入边或出边：全工程没有代码会写出 60，也没有流程会推进 0。
     * 将来要产生它们，先在这里加边，再写带旧状态条件的 UPDATE。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(ProposalStatusEnum to) {
        return switch (this) {
            case FIRST_REVIEW -> to == SECOND_REVIEW || to == PENDING_EXECUTE || to == REJECTED;
            case SECOND_REVIEW -> to == PENDING_EXECUTE || to == REJECTED;
            case PENDING_EXECUTE -> to == EXECUTING || to == FAILED;
            case EXECUTING -> to == SUCCESS || to == FAILED;
            case WAITING, REJECTED, SUCCESS, PARTIAL_SUCCESS, FAILED, RISK_BLOCKED -> false;
        };
    }
}
