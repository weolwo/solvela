package solvela.lottery.issue.dao;

        import java.util.List;

        import solvela.lottery.LotteryIssue;
        import solvela.lottery.issue.domain.query.LotteryIssueQuery;
        import solvela.lottery.issue.domain.dto.LotteryIssueOverviewDTO;
        import solvela.lottery.issue.domain.dto.LotteryIssueDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 期号配置 Dao
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@Mapper
public interface LotteryIssueDao extends BaseMapper<LotteryIssue> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryIssueDTO> queryPage(Page<?> page, @Param("queryForm") LotteryIssueQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryIssueDTO> queryList(@Param("queryForm") LotteryIssueQuery queryForm);

    /**
     * 巡检概览：逾期未开奖 / 售卖中 / 已售罄 / 今日计划开奖，一次扫表算完。
     *
     * 不拆成四条 count：分四次执行会拿到四个不同的 NOW()，
     * 出现「卡片说售卖中 3 期、点进去只有 2 期」这种自相矛盾。
     *
     * 只吃 lotteryCode，其余筛选条件刻意不参与，理由见 mapper 注释。
     */
    LotteryIssueOverviewDTO overview(@Param("queryForm") LotteryIssueQuery queryForm);

    /**
     * 数据库当前时间。
     *
     * 售卖窗口判定必须用它，不能用 JVM 的 LocalDateTime.now() —— 铁律 9/10：
     * 全系统只认数据库一个时钟。多实例部署时各节点 JVM 时钟未必一致，
     * 用 JVM 时间会出现「A 节点认为还能买、B 节点认为已截止」。
     */
    java.time.LocalDateTime selectDbNow();

    /**
     * 已发数 +1，条件更新做防超发兜底。
     *
     * WHERE sold_count < total_count 让 DB 成为最后一道闸门：
     * 即便 Redis 游标判定被绕过（比如误清了 Redis），这里也不会让已发数超过上限。
     *
     * @return 影响行数，0 表示已达上限
     */
    int increaseSoldCount(@Param("id") Long id);

    /**
     * 抢开奖闸门：状态 0->1 并定案开奖号码，一条 SQL 完成。
     *
     * WHERE status = #{from} 是并发闸门 —— 两个运营同时点开奖，
     * 第二个人拿到 rows=0 直接退出，不会重复核销。
     * 开奖号码在这一刻定案且不再变，所以中断重跑的结果必然一致。
     *
     * @return 影响行数，0 表示没抢到（状态已被别人改过）
     */
    int startSettle(@Param("id") Long id, @Param("from") Integer from,
                    @Param("to") Integer to, @Param("winningNumber") String winningNumber);

    /**
     * 核销完成：1->2，并显式写 settle_time。
     *
     * ⚠️ settle_time 没有 ON UPDATE CURRENT_TIMESTAMP 兜底，
     * 必须在这里显式赋值 —— 这是铁律 9「时间只由数据库产生」的例外分支
     * （同 t_proposal_record.approve_time）。用 NOW() 而不是 Java 时间，仍然只有数据库一个时钟。
     */
    int finishSettle(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);


            // ----- 物理删除 -----
                /**
                 * 单个物理删除。
                 *
                 * 只用于清理「零发号 + 待开奖」的空期（守卫见 LotteryIssueService.checkDeletable）——
                 * 发过号的期删不得：t_lottery_record 里的记录既是用户凭证，也是开奖核销的依据。
                 * 批量版本已移除，期号是一期期建的，没有批量删的场景。
                 */
                long deleteById(@Param("id") Long id);
}