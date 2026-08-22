package sa.task.record.dao;

        import java.util.List;
        import sa.task.record.domain.entity.TaskRecord;
        import sa.task.record.domain.form.TaskRecordQueryForm;
        import sa.task.record.domain.vo.TaskRecordVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 任务记录表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */
@Mapper
public interface TaskRecordDao extends BaseMapper<TaskRecord> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskRecordVO> queryPage(Page<?> page, @Param("queryForm") TaskRecordQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<TaskRecordVO> queryList(@Param("queryForm") TaskRecordQueryForm queryForm);

    // ==================== 漏斗统计 ====================

    /**
     * 漏斗计数 + 过期收口体检 + 一致性体检，一次扫表算完。
     *
     * 不拆成多条 count：多次执行不但慢，还可能落在不同数据快照上，
     * 出现「分项加起来不等于总数」这种自相矛盾。
     * 刻意不吃 status / completeTime 条件 —— 那两个正是漏斗要拆解的维度。
     */
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") TaskRecordQueryForm queryForm);

    /**
     * 任务维度分布（接取量 TOP 20），一次查全 —— 逐个任务查一次在任务多起来之后就是 N+1。
     */
    List<java.util.Map<String, Object>> selectTaskStat(@Param("queryForm") TaskRecordQueryForm queryForm);

    /**
     * 事件丢弃分类分布，查的是 {@code t_task_record_flow}（走 idx_t_tsk_flw_stat）。
     *
     * <p>「用户做了事进度却不涨」的答案在这里，而不在记录表里：被丢弃的事件<b>压根没建记录</b>，
     * 记录表怎么翻都看不到它们。
     */
    List<java.util.Map<String, Object>> selectDiscardStat(@Param("queryForm") TaskRecordQueryForm queryForm);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);

    // ==================== 运行态（v3.44.0，方案 §4.4） ====================

    /**
     * 按唯一键取记录（member_id + task_config_id + period_key，对齐 uk_t_tsk_rec_mbr_cfg_prd）
     */
    TaskRecord selectByUniqueKey(@Param("memberId") Long memberId,
                                 @Param("taskConfigId") Long taskConfigId,
                                 @Param("periodKey") String periodKey);

    /**
     * 取该「基础周期」下最新的一轮记录（limit_count 轮次用）。
     *
     * <p>匹配裸键与带轮次后缀的键：{@code 20260801} 与 {@code 20260801#2}、{@code #3}…
     * 按 id 倒序取第一条 —— 轮次是依次创建的，id 最大的就是最新一轮。
     *
     * @return 该周期还没有任何记录时返回 null
     */
    TaskRecord selectLatestRoundByPeriod(@Param("memberId") Long memberId,
                                         @Param("taskConfigId") Long taskConfigId,
                                         @Param("basePeriodKey") String basePeriodKey);

    /**
     * 条件更新原子累加：COUNT / AMOUNT / SIMPLE 的推进方式。
     *
     * <p>把累加本身压进 UPDATE，一条 SQL 完成，靠行锁天然串行 —— <b>零冲突、零重试</b>，
     * 与 t_prize_pool_item.increaseUsedStock / MemberWalletDao.deductBalanceWithVersion
     * / PromotionConfigDao.deductBudget 是同一模式。
     *
     * <p>返回 0 的语义是明确的：<b>任务已不在进行中</b>（已完成/已发奖/已过期），
     * 不是并发冲突，不需要重试，调用方直接落丢弃流水即可。
     *
     * @return 影响行数，0 表示记录已不可推进
     */
    int advanceMetric(@Param("id") Long id, @Param("delta") java.math.BigDecimal delta);

    /**
     * 乐观锁覆写进度与进度详情：STREAK 专用。
     *
     * <p>STREAK 必须先读 lastHitDate 才知道本次是「清零再+1」还是「+1」，读-改-写无法避免，
     * 故用版本号做并发闸门。返回 0 = 版本已变（有并发）或状态已流转，由调用方有限次重试。
     *
     * <p>⚠️ 不要把这个方法用在 COUNT/AMOUNT 上 —— 那是把一个能一条 SQL 解决的问题
     * 主动降级成需要重试的问题。
     */
    int overwriteMetric(@Param("id") Long id,
                        @Param("metric") java.math.BigDecimal metric,
                        @Param("progressData") String progressData,
                        @Param("version") Integer version);

    /**
     * 达标闸门：0-进行中 -> 1-已完成，条件更新保证「达标的那一次」只有一个线程认领。
     *
     * <p>complete_time 必须显式写 now()：这一列<b>没有</b> ON UPDATE 兜底，
     * 与 approve_time / first_review_time 同类（铁律 9 的例外分支）。
     *
     * @return 1 = 本次是达标的那一次；0 = 已被别人标记过
     */
    int markCompleted(@Param("id") Long id);

    /**
     * 最高档发奖完成：1-已完成 -> 2-已发奖。此后不再接受事件推进
     */
    int markDispatched(@Param("id") Long id);

    /**
     * 仅更新进度详情（dispatchedStages 这类展示字段），不碰 current_metric / status。
     *
     * <p>⚠️ 它<b>不是</b>发奖防重的判据，允许滞后 —— 真正的防重是 t_prize_log.uk_external_biz。
     * 判据只留一个，两个判据一定会漂移。
     */
    int updateProgressData(@Param("id") Long id, @Param("progressData") String progressData);

    // ==================== 过期收口（taskRecordExpire 定时任务） ====================

    /**
     * 已过有效期却仍是「进行中」的条数。给试运行用 —— 先看清楚要改多少行再动手。
     *
     * @param now 数据库时钟，由任务上下文传入（铁律 9：不要在这里用 JVM 时间）
     */
    long countExpirableRecord(@Param("now") java.time.LocalDateTime now);

    /**
     * 把过了有效期的进行中记录置为 3-已过期，一次最多 {@code limit} 行。
     *
     * <p>走 {@code idx_t_tsk_rec_expire(status, valid_end_time)} —— 那个索引<b>本来就是
     * 给这个扫描建的</b>，只是扫描一直没写。
     *
     * <p>条件里只认 {@code status = 0}，所以幂等，也不会误伤已完成/已发奖的记录。
     *
     * @return 实际更新行数
     */
    int expireRecordBatch(@Param("now") java.time.LocalDateTime now, @Param("limit") int limit);

    /**
     * 取数据库时钟（铁律 9：时间只认数据库一个时钟，不用 JVM 的 LocalDateTime.now()）
     */
    java.time.LocalDateTime selectDbNow();
}