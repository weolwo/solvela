package net.lab1024.sa.lottery.record.dao;

        import java.util.List;
        import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
        import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
        import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 用户号码记录 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@Mapper
public interface LotteryRecordDao extends BaseMapper<LotteryRecord> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryRecordVO> queryPage(Page<?> page, @Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryRecordVO> queryList(@Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 漏斗计数 + 派发状态 + 一致性体检，一次扫表算完。
     *
     * 不拆成多条 count：多次执行不但慢，还可能落在不同数据快照上，
     * 出现「分项加起来不等于总数」这种自相矛盾。
     * 刻意不吃 winStatus / prizeLevel 条件 —— 那两个正是漏斗要拆解的维度。
     */
    java.util.Map<String, Object> selectFunnel(@Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 奖级分布，按奖级升序。只统计 win_status=2 的行 ——
     * 未中奖行的 prize_level 是 99 占位，不是奖级。
     */
    List<java.util.Map<String, Object>> selectPrizeLevelStat(@Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 按玩法汇总参与人数与中奖注数，供彩票玩法一览一次性取全部玩法的实况。
     * 不做成「逐个玩法查一次」——玩法多起来就是 N+1。
     */
    List<java.util.Map<String, Object>> selectStatByLottery();

    /**
     * 某期已发出的最大游标，供 Redis 冷启动回源用。
     *
     * ⚠️ 必须取 MAX 而不是 COUNT：发号过程中失败会留下空洞（游标消耗了但记录没落库），
     * 此时 COUNT 小于真实游标，回填后会重发已发过的号码、直接撞 uk_issue_ticket。
     *
     * @return 无记录时返回 null（由调用方兜底成 0）
     */
    Long selectMaxSequenceNo(@Param("lotteryCode") String lotteryCode, @Param("issueNo") String issueNo);

    /**
     * 我的号码：按奖级升序（一等奖在前、未中奖的 99 沉底），同奖级按最新领取在前
     */
    List<LotteryRecord> selectMyTickets(@Param("lotteryCode") String lotteryCode,
                                        @Param("issueNo") String issueNo,
                                        @Param("memberName") String memberName);

    /**
     * 按一条奖级规则认领中奖记录（开奖核销的核心动作）。
     *
     * <b>{@code win_status = 0} 这个守卫就是「奖级互斥」的实现</b>：
     * 一张中了一等奖（全号）的票必然也满足二等奖（尾3），
     * 只要按 prizeLevel 升序依次调用本方法，高奖级先把票认走并改掉 win_status，
     * 低奖级的 UPDATE 就再也匹配不到它。等价于 Java 侧 TicketMatcher 的「命中即止」。
     *
     * prize_code 在此刻快照进记录，之后运营改规则也不会让历史中奖结果漂移。
     *
     * @param limit 单批上限。十万级记录一次性 UPDATE 会撑爆 undo log 与锁，必须分批
     * @return 本批认领到的行数，0 表示这条规则已经没有可认领的票了
     */
    int claimByRule(@Param("lotteryCode") String lotteryCode,
                    @Param("issueNo") String issueNo,
                    @Param("matchRule") String matchRule,
                    @Param("matchLength") Integer matchLength,
                    @Param("winningNumber") String winningNumber,
                    @Param("prizeLevel") Integer prizeLevel,
                    @Param("prizeCode") String prizeCode,
                    @Param("limit") int limit);

    /**
     * 全部奖级认领完毕后，剩下的一律判未中奖。
     * prize_level 落 99 而不是留空，让 C 端「我的号码」能直接 ORDER BY prize_level ASC
     */
    int markNoWin(@Param("lotteryCode") String lotteryCode,
                  @Param("issueNo") String issueNo,
                  @Param("limit") int limit);

    /**
     * 待派奖的中奖记录（分页）。
     * 走 idx_dispatch(issue_no, dispatch_status)
     */
    List<LotteryRecord> selectPendingDispatch(@Param("lotteryCode") String lotteryCode,
                                              @Param("issueNo") String issueNo,
                                              @Param("limit") int limit);

    /**
     * 批量标记已投递。用条件更新（AND dispatch_status = 0）避免重复投递被记两次
     */
    int markDispatched(@Param("idList") List<Long> idList);

    /**
     * 核销进度统计，供接口返回与验收核对
     */
    java.util.Map<String, Object> settleSummary(@Param("lotteryCode") String lotteryCode,
                                                @Param("issueNo") String issueNo);


}