package net.lab1024.sa.draw.prizemapping.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 一个奖池的完整概率结构 + 赔付模型 + 体检结论。
 *
 * <p>按奖池分组而不是平铺坑位行，是因为坑位只有放在一起才有意义：
 * 概率区间要按顺序累加、闭环要整池判定、期望赔付要逐坑相加 ——
 * 单看一行映射什么都判断不了，而原先的列表恰恰就是平铺的映射行。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class DrawPoolAnalysisVO {

    @Schema(description = "奖池编码")
    private String poolCode;

    @Schema(description = "奖池名称，奖池配置缺失时为 null")
    private String poolName;

    @Schema(description = "所属活动编码，供跳转工作台深链使用")
    private String activityCode;

    @Schema(description = "活动状态: 0-未上线, 1-已上线。已上线且有告警的最该先处理")
    private Integer activityStatus;

    @Schema(description = "奖池开关: 0-关闭, 1-开启")
    private Integer poolStatus;

    @Schema(description = "消耗资产类型: CREDIT/TICKET/NONE")
    private String costAssetType;

    @Schema(description = "单次抽奖消耗数值")
    private BigDecimal costValue;

    @Schema(description = "坑位数量")
    private Integer slotCount;

    /**
     * 概率总和。必须等于 100 —— 引擎构造快照时若发现未闭环会抛
     * {@code IllegalArgumentException}，而抽奖执行路径<b>没有捕获它</b>，
     * 结果是该奖池每一次抽奖请求都直接报错。
     */
    @Schema(description = "概率总和，必须等于 100")
    private BigDecimal probabilitySum;

    @Schema(description = "概率是否闭环（与 100 的差值在容差内）")
    private Boolean probabilityClosed;

    @Schema(description = "兜底奖项数量，每池最多一个")
    private Integer fallbackCount;

    /**
     * 抽一次平均要赔多少钱 = Σ(坑位概率 × 奖品单价)。
     * 配概率时最该先看的数，而系统此前没有任何一处显示它。
     */
    @Schema(description = "单次抽奖期望赔付成本")
    private BigDecimal expectedCostPerDraw;

    /**
     * 当前还有库存的坑位所占的概率之和。
     *
     * <p>这是「用户现在抽一次，有多大概率真能拿到东西」的直接答案。
     * 配置里的概率是静态的，但库存会被抽空 —— 抽空的坑位虽然还占着概率区间，
     * 命中后只会走降级或直接失败。所以这个数才是奖池的真实健康度：
     * 它掉到 60%，就意味着 40% 的请求要靠兜底接住；兜底也空了，那 40% 就是纯失败。
     */
    @Schema(description = "有货概率覆盖率：仍有库存的坑位概率之和")
    private BigDecimal availableProbability;

    @Schema(description = "兜底奖项是否还有库存，无兜底时为 null")
    private Boolean fallbackHasStock;

    @Schema(description = "DANGER 级告警条数")
    private Integer dangerCount;

    @Schema(description = "WARN 级告警条数")
    private Integer warnCount;

    @Schema(description = "奖池维度的体检告警")
    private List<DrawPoolIssueVO> issueList;

    @Schema(description = "坑位明细，按 sortWeight 升序 —— 顺序即概率区间的累加顺序")
    private List<DrawPoolSlotVO> slotList;
}
