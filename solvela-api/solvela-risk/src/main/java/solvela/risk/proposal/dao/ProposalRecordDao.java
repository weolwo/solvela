package solvela.risk.proposal.dao;

import solvela.enums.ProposalStatusEnum;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.domain.query.ProposalRecordQuery;
import solvela.risk.proposal.domain.dto.ProposalRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 条件流转提案状态：只有当前状态等于 fromStatus 才会更新
     * <p>
     * 这是资产下发的并发闸门：多个线程/重投事件同时进来，只有抢到状态跃迁的那一个拿到 rows=1，
     * 其余拿到 0 直接退出，避免同一提案被重复执行。
     *
     * @return 更新行数，0 表示状态已被别人推进或提案已完结
     */
    int updateStatus(@Param("id") Long id, @Param("fromStatus") ProposalStatusEnum fromStatus, @Param("toStatus") ProposalStatusEnum toStatus);

    /**
     * 无条件写入终态与备注（成功/失败的收尾，不参与并发竞争）
     */
    int updateStatusAndRemark(@Param("id") Long id, @Param("status") ProposalStatusEnum status, @Param("remark") String remark);

    /**
     * 审批流转：只有当前状态等于 fromStatus 才更新，同时记录审批人与意见
     * <p>
     * 条件更新即并发闸门 —— 两个审批人同时点「通过」，只有一个能拿到 rows=1，
     * 另一个拿到 0 会被告知「已被处理」，避免重复审批导致重复发放。
     *
     * @param reviewerField 写入哪个审批人字段：first_reviewer / second_reviewer
     * @return 更新行数，0 表示状态已被别人推进
     */
    int updateReview(@Param("id") Long id,
                     @Param("fromStatus") ProposalStatusEnum fromStatus,
                     @Param("toStatus") ProposalStatusEnum toStatus,
                     @Param("reviewerField") String reviewerField,
                     @Param("reviewer") String reviewer,
                     @Param("comment") String comment);

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
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 按资产类型汇总条数与金额。
     *
     * <p>⚠️ 金额<b>必须</b>按 asset_type 分组：积分、现金、券张数、实物件数不是同一个量纲，
     * 合成一个「总金额」的那个数字没有任何含义。
     */
    List<java.util.Map<String, Object>> selectAssetStat(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 按来源汇总提案数与到账数：哪种玩法在花钱、哪种玩法的奖最容易发不出去。
     */
    List<java.util.Map<String, Object>> selectSourceStat(@Param("queryForm") ProposalRecordQuery queryForm);

    /**
     * 风控拦截原因分布（TOP 10）。
     *
     * <p>只能按 remark 这个自由文本聚类 —— {@code RiskResult.code} 没有落库。
     * 文案一改统计就会裂开，这是已知的脆弱点（对照 t_task_record_flow.discard_code 的做法）。
     */
    List<java.util.Map<String, Object>> selectBlockReasonStat(@Param("queryForm") ProposalRecordQuery queryForm);

    // ==================== 卡单扫描（proposalStuckScan 定时任务） ====================

    /**
     * 卡在下发的提案条数：{@code status IN (30, 40)} 且超过 {@code minutes} 分钟没有任何更新。
     *
     * <p>下发是在提案事务提交后同步调起的，进程中途退出就没有第二次机会 ——
     * 这些提案不会自己往下走，钱既没发出去也没标成失败。
     *
     * @param now 数据库时钟，由任务上下文传入（铁律 9）
     */
    long countStuckDispatch(@Param("now") java.time.LocalDateTime now, @Param("minutes") int minutes);

    /**
     * 卡单样本（最多 {@code limit} 条），给日志用：让人能直接拿单号去查。
     *
     * <p>不返回全量 —— 真卡了几千条，前十条和后十条是同一个原因，
     * 把几千个单号刷进日志只会把真正有用的那行冲掉。
     */
    List<java.util.Map<String, Object>> selectStuckDispatchSample(@Param("now") java.time.LocalDateTime now,
                                                                  @Param("minutes") int minutes,
                                                                  @Param("limit") int limit);

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}