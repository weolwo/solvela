package solvela.risk.proposal.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.domain.dto.ProposalRecordDTO;
import solvela.risk.proposal.domain.query.ProposalRecordQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提案表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@Mapper
public interface ProposalRecordDao extends BaseMapper<ProposalRecord> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordDTO> queryPage(Page<?> page, @Param("queryForm") ProposalRecordQuery queryForm);

    /*
     * ==================== 状态闸门 ====================
     *
     * 🔴 下面每个方法是 ProposalStatusEnum#canTransitTo 里的一条边，SQL 带旧状态条件，
     * 返回影响行数：0 表示状态已被别人推进或提案已完结。不要改成「读出实体 → set → updateById」，
     * 并发下两个线程会同时读到同一个旧状态，同一笔奖被发两次。
     * ProposalRecordStatusSqlTest 断言这些 SQL 与状态机一一对应。
     */

    /**
     * 抢执行权：待执行 → 执行中。资产下发的并发闸门，只有拿到 1 的那个线程往下发。
     */
    int claimForExecution(@Param("id") Long id);

    /**
     * 下发成功：执行中 → 成功。只有抢到执行权的那个线程会调。
     */
    int markSucceeded(@Param("id") Long id, @Param("remark") String remark);

    /**
     * 下发失败：执行中 → 彻底失败。只有抢到执行权的那个线程会调。
     */
    int markFailed(@Param("id") Long id, @Param("remark") String remark);

    /**
     * 抢执行权那一步自己抛了异常：待执行 → 彻底失败。
     *
     * <p>⚠️ 只认待执行，不认执行中：抛异常时分不清那次抢占到底提交了没有。
     * 若其实已经是执行中，这里影响 0 行，提案留在执行中，由卡单扫描（{@link #countStuckDispatch}）报出来；
     * 若认执行中，会把别的线程正在下发的提案改成失败，它发完回写成功时又影响 0 行 —— 钱出去了，单子却是失败。
     */
    int markFailedUnclaimed(@Param("id") Long id, @Param("remark") String remark);

    /**
     * 单层审批一审通过：待一审 → 待执行。
     *
     * <p>审批的五条 UPDATE 同样是并发闸门：两个审批人同时点，只有一个拿到 1，
     * 另一个拿到 0 被告知「已被处理」。
     */
    int passFirstReview(@Param("id") Long id, @Param("reviewer") String reviewer, @Param("comment") String comment);

    /**
     * 双层审批一审通过：待一审 → 待二审。
     */
    int escalateToSecondReview(@Param("id") Long id, @Param("reviewer") String reviewer, @Param("comment") String comment);

    /**
     * 二审通过：待二审 → 待执行。
     */
    int passSecondReview(@Param("id") Long id, @Param("reviewer") String reviewer, @Param("comment") String comment);

    /**
     * 一审驳回：待一审 → 驳回。
     */
    int rejectAtFirstReview(@Param("id") Long id, @Param("reviewer") String reviewer, @Param("comment") String comment);

    /**
     * 二审驳回：待二审 → 驳回。
     */
    int rejectAtSecondReview(@Param("id") Long id, @Param("reviewer") String reviewer, @Param("comment") String comment);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordDTO> queryList(@Param("queryForm") ProposalRecordQuery queryForm);

    // ==================== 漏斗统计 ====================

    /**
     * 漏斗计数 + 审批积压 + 下发卡单 + 一致性体检，一次扫表算完。
     * <p>
     * 不拆成多条 count：多次执行不但慢，还可能落在不同数据快照上，
     * 出现「分项加起来不等于总数」这种自相矛盾。
     * 刻意不吃 status 条件 —— 它正是漏斗要拆解的维度。
     */
    Map<String, Object> selectFunnel(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 按资产类型汇总条数与金额。
     *
     * <p>⚠️ 金额<b>必须</b>按 asset_type 分组：积分、现金、券张数、实物件数不是同一个量纲，
     * 合成一个「总金额」的那个数字没有任何含义。
     */
    List<Map<String, Object>> selectAssetStat(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 按来源汇总提案数与到账数：哪种玩法在花钱、哪种玩法的奖最容易发不出去。
     */
    List<Map<String, Object>> selectSourceStat(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 风控拦截原因分布（TOP 10）。
     *
     * <p>只能按 remark 这个自由文本聚类 —— {@code RiskResult.code} 没有落库。
     * 文案一改统计就会裂开，这是已知的脆弱点（对照 t_task_record_flow.discard_code 的做法）。
     */
    List<Map<String, Object>> selectBlockReasonStat(@Param("queryForm") ProposalRecordQuery queryForm);

    // ==================== 卡单扫描（proposalStuckScan 定时任务） ====================

    /**
     * 卡在下发的提案条数：{@code status IN (30, 40)} 且超过 {@code minutes} 分钟没有任何更新。
     *
     * <p>下发是在提案事务提交后同步调起的，进程中途退出就没有第二次机会 ——
     * 这些提案不会自己往下走，钱既没发出去也没标成失败。
     *
     * @param now 数据库时钟，由任务上下文传入（铁律 9）
     */
    long countStuckDispatch(@Param("now") LocalDateTime now, @Param("minutes") int minutes);

    /**
     * 卡单样本（最多 {@code limit} 条），给日志用：让人能直接拿单号去查。
     *
     * <p>不返回全量 —— 真卡了几千条，前十条和后十条是同一个原因，
     * 把几千个单号刷进日志只会把真正有用的那行冲掉。
     */
    List<Map<String, Object>> selectStuckDispatchSample(@Param("now") LocalDateTime now,
                                                                  @Param("minutes") int minutes,
                                                                  @Param("limit") int limit);

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}