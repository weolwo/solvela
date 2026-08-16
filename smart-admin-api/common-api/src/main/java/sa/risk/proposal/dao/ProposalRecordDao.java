package sa.risk.proposal.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import sa.risk.proposal.domain.entity.ProposalRecord;
import sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import sa.risk.proposal.domain.vo.ProposalRecordVO;
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
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordVO> queryPage(Page<?> page, @Param("queryForm") ProposalRecordQueryForm queryForm);

    /**
     * 条件流转提案状态：只有当前状态等于 fromStatus 才会更新
     *
     * 这是资产下发的并发闸门：多个线程/重投事件同时进来，只有抢到状态跃迁的那一个拿到 rows=1，
     * 其余拿到 0 直接退出，避免同一提案被重复执行。
     *
     * @return 更新行数，0 表示状态已被别人推进或提案已完结
     */
    int updateStatus(@Param("id") Long id, @Param("fromStatus") Integer fromStatus, @Param("toStatus") Integer toStatus);

    /**
     * 无条件写入终态与备注（成功/失败的收尾，不参与并发竞争）
     */
    int updateStatusAndRemark(@Param("id") Long id, @Param("status") Integer status, @Param("remark") String remark);

    /**
     * 审批流转：只有当前状态等于 fromStatus 才更新，同时记录审批人与意见
     *
     * 条件更新即并发闸门 —— 两个审批人同时点「通过」，只有一个能拿到 rows=1，
     * 另一个拿到 0 会被告知「已被处理」，避免重复审批导致重复发放。
     *
     * @param reviewerField 写入哪个审批人字段：first_reviewer / second_reviewer
     * @return 更新行数，0 表示状态已被别人推进
     */
    int updateReview(@Param("id") Long id,
                     @Param("fromStatus") Integer fromStatus,
                     @Param("toStatus") Integer toStatus,
                     @Param("reviewerField") String reviewerField,
                     @Param("reviewer") String reviewer,
                     @Param("comment") String comment);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordVO> queryList(@Param("queryForm") ProposalRecordQueryForm queryForm);

    // ==================== 漏斗统计 ====================

    /**
     * 漏斗计数 + 审批积压 + 下发卡单 + 一致性体检，一次扫表算完。
     *
     * 不拆成多条 count：多次执行不但慢，还可能落在不同数据快照上，
     * 出现「分项加起来不等于总数」这种自相矛盾。
     * 刻意不吃 status 条件 —— 它正是漏斗要拆解的维度。
     */
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") ProposalRecordQueryForm queryForm);

    /**
     * 按资产类型汇总条数与金额。
     *
     * <p>⚠️ 金额<b>必须</b>按 asset_type 分组：积分、现金、券张数、实物件数不是同一个量纲，
     * 合成一个「总金额」的那个数字没有任何含义。
     */
    List<java.util.Map<String, Object>> selectAssetStat(@Param("queryForm") ProposalRecordQueryForm queryForm);

    /**
     * 按来源汇总提案数与到账数：哪种玩法在花钱、哪种玩法的奖最容易发不出去。
     */
    List<java.util.Map<String, Object>> selectSourceStat(@Param("queryForm") ProposalRecordQueryForm queryForm);

    /**
     * 风控拦截原因分布（TOP 10）。
     *
     * <p>只能按 remark 这个自由文本聚类 —— {@code RiskResult.code} 没有落库。
     * 文案一改统计就会裂开，这是已知的脆弱点（对照 t_task_record_flow.discard_code 的做法）。
     */
    List<java.util.Map<String, Object>> selectBlockReasonStat(@Param("queryForm") ProposalRecordQueryForm queryForm);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);
}