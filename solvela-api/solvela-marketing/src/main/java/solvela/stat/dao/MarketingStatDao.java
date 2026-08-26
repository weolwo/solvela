package solvela.stat.dao;

import solvela.stat.domain.dto.EventHealthDTO;
import solvela.stat.domain.dto.GameplayDTO;
import solvela.stat.domain.dto.OverviewDTO;
import solvela.stat.domain.dto.ParticipationByTypeDTO;
import solvela.stat.domain.dto.ParticipationTrendDTO;
import solvela.stat.domain.dto.PrizeHealthDTO;
import solvela.stat.domain.dto.TaskFunnelDTO;
import solvela.stat.domain.dto.TopMemberDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销统计 Dao
 *
 * <p>口径集中写在 MarketingStatMapper.xml 的头注释里，改 SQL 前先读那段。
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Mapper
public interface MarketingStatDao {

    // ───────── 首页参与统计 ─────────

    /**
     * 参与趋势：按天 × 玩法类型，统计参与人数
     *
     * @param fromTime 起始时间（含）
     */
    List<ParticipationTrendDTO> participationTrend(@Param("fromTime") LocalDateTime fromTime);

    /**
     * 各玩法类型参与人数
     *
     * @param fromTime 起始时间（含）
     */
    List<ParticipationByTypeDTO> participationByType(@Param("fromTime") LocalDateTime fromTime);

    // ───────── 板块一：活动总览 ─────────

    /** 全部活动，按创建时间倒序。玩法数与完备度不在 SQL 里算，走 ActivityRefProvider */
    List<OverviewDTO.ActivityCard> activityCardList();

    /**
     * 逾期未开奖：已过计划开奖时刻但仍未开奖（status &lt;&gt; 2）。
     * 属客诉级异常，比任何参与量指标都该优先展示。
     */
    List<OverviewDTO.OverdueIssue> overdueIssueList();

    // ───────── 板块二：发奖健康度 ─────────

    /** 发奖汇总：条数三态 + 待审积压 + 已发出价值。activityCode 为空则统计全局 */
    PrizeHealthDTO prizeSummary(@Param("activityCode") String activityCode);

    /** 按资产类型拆分的条数与价值 */
    List<PrizeHealthDTO.AssetItem> prizeByAsset(@Param("activityCode") String activityCode);

    /** 失败原因 TOP 10 */
    List<PrizeHealthDTO.FailReason> prizeFailReasonTop(@Param("activityCode") String activityCode);

    /** 发出价值趋势：按天 × 资产类型 */
    List<PrizeHealthDTO.TrendItem> prizeTrend(@Param("activityCode") String activityCode,
                                             @Param("fromTime") LocalDateTime fromTime);

    // ───────── 板块三：玩法运行态 ─────────

    /** 抽奖状态分布 */
    List<GameplayDTO.DrawStatItem> drawStat(@Param("activityCode") String activityCode);

    /** 该活动下所有彩票玩法的期号 */
    List<GameplayDTO.LotteryIssueItem> lotteryIssueList(@Param("activityCode") String activityCode);

    /** 该活动下的任务列表（带参与人数），供前端选一个看漏斗 */
    List<GameplayDTO.TaskOption> taskOptionList(@Param("activityCode") String activityCode);

    /** 任务阶梯档位与到达人数 */
    List<TaskFunnelDTO.StageItem> taskFunnelStages(@Param("taskConfigId") Long taskConfigId);

    /** 任务接取人数（有记录的人数） */
    Integer taskJoinedCount(@Param("taskConfigId") Long taskConfigId);

    /** 任务名称，给漏斗标题用 */
    String taskName(@Param("taskConfigId") Long taskConfigId);

    // ───────── 板块四：事件健康度 ─────────

    /** 按 flow_type 统计条数：1-有效推进 2-丢弃 */
    List<java.util.Map<String, Object>> eventFlowTypeCount(@Param("fromTime") LocalDateTime fromTime);

    /** 丢弃原因聚类（按 discard_code，不是自由文本的 discard_reason） */
    List<EventHealthDTO.DiscardItem> discardStat(@Param("fromTime") LocalDateTime fromTime);

    // ───────── 板块五：用户维度 ─────────

    /** Top 获奖用户，按已发出价值降序 */
    List<TopMemberDTO> topMembers(@Param("fromTime") LocalDateTime fromTime, @Param("limit") int limit);
}
