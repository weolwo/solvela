package sa.ledger.coupon.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 优惠券统计：这段时间<b>发了多少张</b>、<b>核销了多少张</b>，以及手上还压着多少张。
 *
 * <h3>发放和核销是两个口径，绝对不能用同一个时间窗</h3>
 * 发放按 {@code create_time}，核销按 {@code used_time}。
 * 如果用同一个窗口算「今日核销率 = 今日核销 / 今日发放」，得到的数字必然接近 0 ——
 * 今天刚发出去的券当然还没来得及用。那个数字不是「核销率低」，而是<b>口径本身就错了</b>，
 * 而它错得很像真的：一个 2% 的核销率看上去就是一条正常的业务结论。
 *
 * <p>所以这里给三组各自独立的数：
 * <ol>
 *   <li><b>本期发放</b>：时间窗落在 {@code create_time} 上；</li>
 *   <li><b>本期核销</b>：时间窗落在 {@code used_time} 上 —— 今天核销的券可能是上个月发的；</li>
 *   <li><b>券库存</b>：<b>不受时间范围影响</b>，是全量。压着多少张没用的券是个存量问题，
 *       限制在今天只会把它藏起来。</li>
 * </ol>
 *
 * <h3>一个没人查就发现不了的数字</h3>
 * <b>已过有效期却仍是「未使用」</b>：全工程<b>没有任何地方</b>把券置为 2-已过期
 * （没有这样的定时任务，也没有任何 Java 代码写这个状态）。这些券不会自己收口，
 * 用户端会一直看到一张永远用不了的券，而按状态统计时它们还一直算在「未使用」里 ——
 * 也就是说「未使用」这个数本身是虚高的。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class MemberCouponStatVO {

    // ---------------- 本期发放（时间窗落在 create_time） ----------------

    @Schema(description = "本期发放张数：create_time 落在时间范围内")
    private Long issuedCount;

    @Schema(description = "本期发放涉及的会员数（去重）")
    private Long issuedMemberCount;

    /**
     * 本期发放的这批券里已经被用掉的张数。
     * 分母是「同一批券」，所以这个比率是有意义的 —— 但刚发的券天然还没来得及用，
     * 时间范围选得越近，它越低。
     */
    @Schema(description = "本期发放的券中已使用的张数")
    private Long issuedUsedCount;

    @Schema(description = "本期发放券的核销率 = 本期发放中已使用 / 本期发放")
    private BigDecimal issuedUsedRate;

    // ---------------- 本期核销（时间窗落在 used_time，与上面不是同一批券） ----------------

    /**
     * 时间窗落在 {@code used_time} 上。今天核销的券可能是上个月发的 ——
     * 这个数和上面的「本期发放」没有包含关系，不要相减。
     */
    @Schema(description = "本期核销张数：used_time 落在时间范围内")
    private Long usedCount;

    @Schema(description = "本期核销涉及的会员数（去重）")
    private Long usedMemberCount;

    // ---------------- 券库存（全量，不受时间范围影响） ----------------

    @Schema(description = "券总张数（全量）")
    private Long stockTotalCount;

    /**
     * ⚠️ 这个数是<b>虚高</b>的：没有过期扫描任务，过了有效期的券也还挂在这里。
     * 真正还能用的是 {@code stockUnusedCount - staleUnusedCount}。
     */
    @Schema(description = "未使用张数（全量）：status=0，含已过有效期但没人置过期的")
    private Long stockUnusedCount;

    @Schema(description = "已使用张数（全量）：status=1")
    private Long stockUsedCount;

    @Schema(description = "已过期张数（全量）：status=2。全工程没有任何地方写这个状态")
    private Long stockExpiredCount;

    @Schema(description = "已作废张数（全量）：status=3")
    private Long stockVoidCount;

    /**
     * 已过有效期却仍是「未使用」。没有过期扫描任务，这些券永远不会自己收口。
     */
    @Schema(description = "已过有效期仍是未使用（全量）：status=0 且 valid_end_time < now()")
    private Long staleUnusedCount;

    /**
     * 七天内到期且还没用的券。运营催一催还来得及 —— 过期之后就只能是一次失败的发放。
     */
    @Schema(description = "7 天内到期且未使用（全量）")
    private Long expiringSoonCount;

    // ---------------- 分布 ----------------

    @Schema(description = "券模维度分布（按本期发放量降序，TOP 10）")
    private List<CouponStatVO> couponList;

    @Schema(description = "来源维度分布（本期发放）")
    private List<SourceStatVO> sourceList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一种券模在本期的发放与核销。
     */
    @Data
    public static class CouponStatVO {

        @Schema(description = "券模编码")
        private String couponCode;

        @Schema(description = "券名称（取同组样本；改过名的历史行可能与最新配置不一致）")
        private String couponName;

        @Schema(description = "本期发放张数")
        private Long issuedCount;

        @Schema(description = "涉及会员数")
        private Long memberCount;

        @Schema(description = "本期发放中已使用的张数")
        private Long usedCount;

        @Schema(description = "核销率 = 已使用 / 本期发放")
        private BigDecimal usedRate;

        /**
         * 本期发放的这批券里，已经过了有效期还没被用掉的张数。
         */
        @Schema(description = "本期发放中已过期未使用的张数")
        private Long staleCount;
    }

    /**
     * 一个来源的发放情况。
     *
     * <p>{@code source_type} 的 DDL 注释写的是 DRAW/TASK/MANUAL_SEND，
     * 但这一列同样没有枚举约束，所以原样回显取值，不做归类。
     */
    @Data
    public static class SourceStatVO {

        @Schema(description = "来源类型原值")
        private String sourceType;

        @Schema(description = "本期发放张数")
        private Long issuedCount;

        @Schema(description = "占本期发放的比例")
        private BigDecimal issuedShare;
    }
}
