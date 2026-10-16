package solvela.member.risk.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 看板要的四组数（方案 §6.3）。
 *
 * <h3>它回答的是「阈值该往哪边调」</h3>
 * 单看一条流水只能回答「这个人为什么被拦」；而「要不要把某条规则切生效、
 * 全局滑块什么时候关掉」只能看聚合 —— 打扰了多少人、拦下了多少、不一致的有多少。
 *
 * <h3>🔴 四组数都带时间范围，而且范围是必填的</h3>
 * 两张表按月分区，不带 {@code create_time} 条件就会扫所有分区。
 * 看板天然是「最近 N 天」，所以这件事不难办 —— 难办的是忘了它。
 */
@Data
public class MemberRiskStatDTO {

    /** 每天 × 结论的尝试数。堆叠柱状图 */
    private List<DecisionDaily> decisionDaily;

    /** 处置矩阵各格的尝试数。看哪一格最常命中 —— 那一格的配置最该被盯着 */
    private List<MatrixCell> matrixCells;

    /** 核身打扰与通过 */
    private OtpFunnel otpFunnel;

    /** 生效结论与陪跑结论不一致的尝试数 —— 「切生效会多打扰多少人」就是这个数 */
    private Long shadowDiffCount;

    /** 窗口内的总尝试数，给上面几个数当分母 */
    private Long totalCount;

    /**
     * 每条规则「切到生效会怎样」的那三个数（方案 §8.2）。
     *
     * <h3>🔴 它是 P2 那一步唯一的依据</h3>
     * 「把某条陪跑规则切生效」不是一个可以凭感觉拍的决定。没有这几个数，
     * 要么不敢切（规则白写了），要么硬切（上线第一天打扰一片真人）。
     * 以前这三个数要对着分区表手写一段 join —— 那种事只会在出了问题之后才有人去做。
     */
    private List<RuleVerdict> ruleVerdicts;

    /**
     * IP 熟悉度分布。<b>关掉全局滑块的前置条件之一</b>（§9）：
     * 「严格熟悉网段」的占比够高，关掉之后才有足够多的人真的免掉滑块；
     * 占比很低的话，关掉只是把滑块从「所有人」变成「几乎所有人」。
     */
    private List<FamiliarityBucket> familiarity;

    @Data
    public static class DecisionDaily {
        private LocalDate day;
        private Integer decision;
        private Long count;
    }

    @Data
    public static class MatrixCell {
        private Integer deviceTrust;
        private Integer ipFamiliar;
        private Integer dormant;
        private Long count;
    }

    /**
     * 核身漏斗。
     *
     * @param challenged 判了要 OTP 并且真签了票的次数（= 打扰了多少次）
     * @param verified   其中后来验过码的次数。🔴 靠 {@code ref_event_id} 指回签票那一行来数 ——
     *                   不然「要了码的人后来验过没有」查不出来，而那正是打扰率的分子
     */
    public record OtpFunnel(long challenged, long verified) {
    }

    /** 一条规则切到生效的代价与收益（方案 §8.2 的三个数 + 两个辅助数） */
    @Data
    public static class RuleVerdict {

        private String ruleCode;

        /** 命中时规则所处的档位：1-陪跑, 2-生效。规则档位会变，命中明细里存的是当时的 */
        private Integer mode;

        private Long hits;

        /** 其中最后登录成功了的 —— 「打扰率」的分母侧参考 */
        private Long loginSucceeded;

        /** 其中被事后标成「确认被盗」的。有就是赚的；早期标注少，不能单看它 */
        private Long confirmedTakeover;

        /**
         * 其中<b>会改变结论</b>的（生效结论 ≠ 陪跑结论）。
         *
         * <p>🔴 这个数就是「切过去会多打扰多少人」—— P2 那一步看的就是它。
         */
        private Long wouldChange;

        /** 其中「特征取不到、按未命中处理」的。≈ 0 才说明这条规则真的在工作 */
        private Long errorCount;
    }

    @Data
    public static class FamiliarityBucket {
        /** 见 RiskIpFamiliarity：0-陌生, 1-同城同运营商, 2-常用网段 */
        private Integer ipFamiliar;
        private Long count;
    }

}
