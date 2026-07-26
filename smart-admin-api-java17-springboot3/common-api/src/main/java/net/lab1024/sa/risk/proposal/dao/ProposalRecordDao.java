package net.lab1024.sa.risk.proposal.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import net.lab1024.sa.risk.proposal.domain.vo.ProposalRecordVO;
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