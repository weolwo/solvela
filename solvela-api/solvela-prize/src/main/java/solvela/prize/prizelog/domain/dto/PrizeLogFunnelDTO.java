package solvela.prize.prizelog.domain.dto;


import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 奖励漏斗：把一堆发奖流水压成「奖到底发出去了没有、卡在哪、为什么没发出去」。
 *
 * <p>原先的记录页是一屏裸字段 + 逐行的小感叹号，翻十页也答不出运营每天最该问的三件事：
 * 这段时间发出去多少（<b>条数与价值是两个数</b>）、有没有人挂在审批池里没人管、
 * 有没有奖卡在半路既没发出去也没标失败。
 *
 * <h3>两个必须拆开、绝不能合并的口径</h3>
 * <ol>
 *   <li><b>条数 ≠ 价值</b>：《营销中台-数据统计方案》§2.1 实测过 —— 315 条发奖记录里，
 *       除了券<b>一分钱都没真正发出去</b>（积分预算耗尽失败、现金与实物全挂在待审批）。
 *       首屏只写「累计发奖 315 次」会让人以为活动跑得很好。所以两个数并列展示，
 *       且价值必须按 {@code prize_type} 拆开 —— 积分、现金、券面额、实物价值不是同一个量纲；</li>
 *   <li><b>「已发出」不是「已到账」</b>：营销域只知道自己把发奖指令发出去了，
 *       钱有没有真到用户手上是账务域的事（§2.2）。所以本页所有措辞一律是「已发出」，
 *       写成「已发放/已到账」会让运营以为用户收到了。</li>
 * </ol>
 *
 * <h3>三个「没人查就发现不了」的数字</h3>
 * <ol>
 *   <li><b>待审积压</b>（{@code approve_status=1}）：{@code approve_mode=1} 的奖品唯一的出口
 *       就是有人来点「通过」，没人点它就永远停在这里。实测最早一条曾挂了 140 小时（近 6 天）——
 *       而它在任何「发奖总数」里都看不出来。所以这里同时给出最久的一条等了多少分钟；</li>
 *   <li><b>卡在等待执行</b>（{@code status=0} 且不是在等审批）：
 *       ⚠️ 这个数<b>不能直接当成故障</b> —— 提案若进了财务审批池，发奖记录合理地停在 0-等待执行
 *       （见 {@code PrizeDispatchHandler.doDispatch} 的注释）。本表分不出这两种情况，
 *       要拿 {@code external_biz_no} 去「提案记录」页按来源单号查。
 *       但停留时间越长越不可能是前者：下发在提案事务提交后同步调起，
 *       进程中途退出就没有第二次机会，工程里<b>没有任何重试/补偿任务</b>；</li>
 *   <li><b>奖励体值不是数字</b>：四个发奖策略（Score/Balance/Coupon/Physical）开头全是
 *       {@code new BigDecimal(prizeLog.getPrizeValue())}，而这一列是 {@code varchar}。
 *       解析不了就当场发奖失败，而且这批行还会让「已发出价值」少算 —— 静默少算，没人会发现。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class PrizeLogFunnelDTO {

    /** 发奖记录总数（筛选范围内） */
    private Long totalCount;

    /** 涉及会员数（去重） */
    private Long memberCount;

    // ---------------- 执行状态分布 ----------------

    /**
     * {@code status=1}。措辞只能是「已发出」：营销域只发出了指令，是否到账要看账务域。
     */
    private Long successCount;

    /** 等待执行：status=0 */
    private Long waitingCount;

    /** 发放失败：status=2 */
    private Long failedCount;

    /** 已发出率 = 已发出 / 总数。是「指令发出」的比例，不是到账率 */
    private BigDecimal successRate;

    // ---------------- 审批分布 ----------------

    /** 无需审批：approve_status=0 */
    private Long approveNoneCount;

    /**
     * 本页唯一需要人动手处理的东西：{@code approve_mode=1} 的奖品只有审批通过才会派发。
     */
    private Long approvePendingCount;

    /** 已批准：approve_status=2 */
    private Long approvePassedCount;

    /** 已驳回：approve_status=3 */
    private Long approveRejectedCount;

    /**
     * 待审积压里最久的一条已经等了多少分钟。只看条数看不出「挂了六天」。
     */
    private Long approveOldestMinutes;

    /**
     * {@code status=0} 且 {@code approve_status<>1} 且 30 分钟没动过。
     * 不等于故障：可能是提案在财务审批池里。判断要拿来源单号去提案记录页对。
     */
    private Long stuckWaitingCount;

    // ---------------- 分布 ----------------

    /** 奖励类型维度：条数与价值双口径，价值按类型分开算 */
    private List<PrizeTypeStatDTO> typeList;

    /** 奖品维度分布，按发奖量降序（TOP 20） */
    private List<PrizeStatDTO> prizeList;

    /** 失败原因分布（TOP 10） */
    private List<FailReasonDTO> failReasonList;

    /** 数据一致性与流程体检告警 */
    private List<String> issueList;

    /**
     * 一种奖励类型的发放情况。<b>价值只在同一 prizeType 内可加。</b>
     *
     * <p>⚠️ {@code prize_value} 在四种类型下都是「金额/数值」而不是件数：
     * 券是<b>面额</b>（{@code CouponHandler} 里的变量就叫 amount，一次固定发一张），
     * 实物是<b>价值</b>。DDL 里那句「积分数/券ID」的列注释是早年的，已经和代码对不上了。
     */
    @Data
    public static class PrizeTypeStatDTO {

        /**
         * 只回编码，中文名与单位由前端字典解析 —— 在这里再手写一份就是第二个真相源。
         */
        private String prizeType;

        /** 记录条数 */
        private Long logCount;

        /** 已发出条数：status=1 */
        private Long successCount;

        /** 已发出率 */
        private BigDecimal successRate;

        /** 已发出价值 = SUM(prize_value)，仅统计 status=1 且体值可解析的行 */
        private BigDecimal successValue;

        /** 在途价值：status=0，指令还没执行完，钱还没出去 */
        private BigDecimal waitingValue;

        /** 失败价值：status=2，这部分是用户没拿到的 */
        private BigDecimal failedValue;

        /**
         * 体值解析不了的条数。这批行的价值<b>没有</b>计入上面三个金额 ——
         * 不标出来就是静默少算。
         */
        private Long badValueCount;
    }

    /**
     * 一个奖品的发放情况：哪个奖发得多、哪个奖最容易发不出去。
     */
    @Data
    public static class PrizeStatDTO {

        /** 奖品编码 */
        private String prizeCode;

        /** 奖品名称（取同组样本；改过名的历史行可能与最新配置不一致） */
        private String prizeName;

        /** 奖励类型 */
        private String prizeType;

        /** 记录条数 */
        private Long logCount;

        /** 已发出条数 */
        private Long successCount;

        /** 等待执行条数 */
        private Long waitingCount;

        /** 失败条数 */
        private Long failedCount;

        /** 待审批条数 */
        private Long pendingCount;

        /** 已发出率 */
        private BigDecimal successRate;
    }

    /**
     * 一类失败原因。
     *
     * <p>⚠️ 只能按 {@code fail_reason} 的<b>文案原文</b>聚类，因为这张表没有
     * {@code fail_code} 那样的封闭编码列（对比 {@code t_proposal_record.risk_code}
     * 与 {@code t_task_record_flow.discard_code}）。文案一旦带上具体数值，
     * 同一种原因就会裂成很多条 —— 这是已知代价，不是可以靠前端合并掩盖的东西，
     * 所以这里如实按原文展示，不编造分类。
     */
    @Data
    public static class FailReasonDTO {

        /** 失败原因原文；写入侧没写时为 null */
        private String failReason;

        /** 条数 */
        private Long failCount;

        /** 占全部失败的比例 */
        private BigDecimal failShare;
    }
}
