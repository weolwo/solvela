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

    /** 按规则的命中数。生效 / 陪跑分开，取特征失败的也单独数 */
    private List<RuleHit> ruleHits;

    /** 生效结论与陪跑结论不一致的尝试数 —— 「切生效会多打扰多少人」就是这个数 */
    private Long shadowDiffCount;

    /** 窗口内的总尝试数，给上面几个数当分母 */
    private Long totalCount;

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

    @Data
    public static class RuleHit {
        private String ruleCode;
        /** 命中时规则所处的档位：1-陪跑, 2-生效 */
        private Integer mode;
        private Long count;
        /** 其中「特征取不到、按未命中处理」的次数。不为 0 说明这条规则其实没在工作 */
        private Long errorCount;
    }
}
