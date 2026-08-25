package solvela.prize.prizelog.dao;

        import java.util.List;

        import solvela.prize.prizelog.domain.entity.PrizeLog;
        import solvela.prize.prizelog.domain.form.PrizeLogQueryForm;
        import solvela.prize.prizelog.domain.vo.PrizeLogVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖励记录表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@Mapper
public interface PrizeLogDao extends BaseMapper<PrizeLog> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryPage(Page<?> page, @Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 按业务单号回写派发终态（0-等待, 1-成功, 2-失败）
     *
     * 资产下发在提案事务提交后才执行，结果回不到 PrizeDispatchHandler 的调用栈，
     * 只能由资产分发引擎反向更新，否则发奖记录会停在「成功」而用户实际没收到。
     * 只更新仍处于 0-等待执行 的记录，避免覆盖人工订正过的终态。
     *
     * @return 更新行数
     */
    int updateStatusByExternalBizNo(@Param("externalBizNo") String externalBizNo,
                                    @Param("status") Integer status,
                                    @Param("failReason") String failReason);

    /**
     * 发奖审批流转：只有当前审批状态等于 fromApproveStatus 才更新
     *
     * 条件更新即并发闸门，防止两个运营同时点通过导致重复派发。
     *
     * @return 更新行数，0 表示已被别人处理
     */
    int updateApproveStatus(@Param("id") Long id,
                            @Param("fromApproveStatus") Integer fromApproveStatus,
                            @Param("toApproveStatus") Integer toApproveStatus,
                            @Param("approveBy") String approveBy);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryList(@Param("queryForm") PrizeLogQueryForm queryForm);

    // ==================== 漏斗统计 ====================

    /**
     * 漏斗计数 + 审批积压 + 卡单 + 一致性体检，一次扫表算完。
     *
     * <p>不拆成多条 count：多次执行不但慢，还可能落在不同数据快照上，
     * 出现「分项加起来不等于总数」这种自相矛盾。
     *
     * <p>刻意不吃 {@code status} / {@code approveStatus} 两个筛选项 ——
     * 那两个正是漏斗要拆解的维度，跟着筛会让「已发出率」恒为 100%。
     */
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 奖励类型维度：条数与价值双口径。
     *
     * <p>价值必须按类型分组 —— 积分、现金、券面额、实物价值不是同一个量纲，
     * 合成一个「总价值」的那个数字没有任何含义。
     */
    List<java.util.Map<String, Object>> selectPrizeTypeStat(@Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 奖品维度分布（发奖量 TOP 20）：哪个奖发得多、哪个奖最容易发不出去。
     */
    List<java.util.Map<String, Object>> selectPrizeStat(@Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 失败原因分布（TOP 10），按 {@code fail_reason} 文案原文聚类。
     *
     * <p>⚠️ 这张表没有 {@code fail_code} 那样的封闭编码列（对比 t_proposal_record.risk_code），
     * 所以文案一旦带上具体数值，同一种原因就会裂成多条。已知代价，如实展示原文。
     */
    List<java.util.Map<String, Object>> selectFailReasonStat(@Param("queryForm") PrizeLogQueryForm queryForm);

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}