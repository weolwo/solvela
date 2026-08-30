package solvela.risk.promotiongroup.domain.dto;

import lombok.Data;
import solvela.enums.EnableStatusEnum;
import solvela.enums.ReviewLevelEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台聚合回显。与 {@code PromotionGroupWorkbenchSaveCommand} <b>同构</b>，
 * 前端拿到即可直接填回表单，不用再做一层字段搬运 —— 与彩票工作台的做法一致。
 *
 * <p>比保存命令多出来的只有两样：子项上的 {@code usedQuota} / {@code usedAmount}。
 * 它们是只读的运行态水位，工作台要显示「这个池子还剩多少」，但绝不回传。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupWorkbenchDTO {

    private Long id;

    private String groupCode;

    private String groupName;

    private String remark;

    private EnableStatusEnum status;

    private List<PromotionGroupItemDTO> itemList;

    @Data
    public static class PromotionGroupItemDTO {

        private Long id;

        private String prizeType;

        private String promoName;

        private Integer totalQuota;

        /** 只读：由发放链路的原子 SQL 维护，工作台只展示不回传 */
        private Integer usedQuota;

        private BigDecimal totalAmount;

        /** 只读：同上 */
        private BigDecimal usedAmount;

        private ReviewLevelEnum reviewLevel;

        private BigDecimal firstReviewThreshold;

        private BigDecimal secondReviewThreshold;

        private Integer singleMaxQuota;

        private BigDecimal singleMaxAmount;

        private String limitPeriod;

        private LocalDateTime limitStartTime;

        private LocalDateTime limitEndTime;

        private Integer identifyLimit;

        private Integer phoneLimit;

        private Integer ipLimit;

        private Integer deviceLimit;

        private Integer fingerprintLimit;

        private String mutexRule;

        private EnableStatusEnum status;
    }
}
