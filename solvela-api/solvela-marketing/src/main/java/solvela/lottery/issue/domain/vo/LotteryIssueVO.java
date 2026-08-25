package solvela.lottery.issue.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 期号配置 列表VO
 *
 * <p>末尾四个字段（lotteryName / totalCount / saleState / overdue）是<b>查询时算出来的派生值</b>，
 * 表里没有对应列，也刻意不冗余存 —— 冗余就意味着两处可改、迟早不一致
 * （同 {@code increaseSoldCount} 里 total_count 走 join 而不存一份的理由）。
 *
 * <p>⚠️ saleState 与 overdue 都依赖「现在几点」，一律在 SQL 里用 {@code NOW()} 算。
 * 前端拿到的是结论而不是原料，浏览器不需要、也不允许自己再算一遍 ——
 * 那就是第二个时钟源（铁律 9/10），必然与运行态发号的判定漂移。
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
public class LotteryIssueVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "期号")
    private String issueNo;

    @Schema(description = "已售/已派发数量")
    private Integer soldCount;

    @Schema(description = "售卖开始时间")
    private LocalDateTime saleStartTime;

    @Schema(description = "售卖结束时间")
    private LocalDateTime saleEndTime;

    @Schema(description = "计划开奖时间：对外承诺的开奖时刻，可编辑")
    private LocalDateTime planDrawTime;

    @Schema(description = "实际开奖时间：由开奖流程写入，只读")
    private LocalDateTime settleTime;

    @Schema(description = "开奖号码")
    private String winningNumber;

    @Schema(description = "状态: 0-待开奖, 1-售卖中, 2-已开奖")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    // ---------------- 以下为查询时派生，非表字段 ----------------

    @Schema(description = "玩法名称，join t_lottery_config 取")
    private String lotteryName;

    /**
     * 所属活动编码，join t_lottery_config 取。
     * 列表不展示，只用于「去工作台」深链 —— 工作台是按 activityCode + lotteryCode 定位的，
     * 少了它就只能把运营丢到工作台首页让他自己再找一遍。
     */
    @Schema(description = "所属活动编码，供跳转工作台深链使用")
    private String activityCode;

    @Schema(description = "本期发行总数上限，join t_lottery_config 取，作为已发数的分母")
    private Integer totalCount;

    /**
     * 「这一期现在还能不能领号」的结论。
     *
     * <p>取值与判定顺序<b>逐条对齐运行态</b> {@code TicketIssueService#obtain}，
     * 两边必须一起改：玩法上线 → 期号待开奖 → 未到开始 → 已过结束 → 售罄。
     * 顺序不能换：售罄判定在窗口之后，所以一个「还没开售」的期不会被显示成售罄。
     *
     * <p>它与 status 是两个维度：status 是生命周期（待开奖/核销中/已开奖），
     * 这个是售卖态。列表上必须两个都显示，否则「待开奖」这一个标签会同时盖住
     * 「还没开售」「正在售」「已停售待开」三种完全不同的处境。
     */
    @Schema(description = "售卖态: 0-未开始, 1-售卖中, 2-已结束, 3-已停止发号(已开奖/核销中), 4-玩法不可售, 5-已售罄")
    private Integer saleState;

    /**
     * 已过计划开奖时间却还没开完奖。这是本页存在的理由 ——
     * 工作台一次只看一个玩法，看不到「哪几期到点了没人开」。
     */
    @Schema(description = "是否逾期未开奖：plan_draw_time 已过且 status 未到已开奖")
    private Boolean overdue;

}
