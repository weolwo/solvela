package sa.domain.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import sa.enums.EventCategoryEnum;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 【跨域契约】用户中奖领域事件
 */
@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserPrizeEvent extends BaseBizEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    @Builder.Default
    private String category = EventCategoryEnum.PRIZE.name();
    // ================== 2. 溯源与路由信息 ==================
    private String sourceBizId;   // 来源单号（LotteryRecord的ID）
    private String activityCode;  // 追踪用：活动/彩票编码

    // ================== 3. 用户信息 ==================
    private Long memberId;        // 用户ID单靠 memberName 不安全，容易重名或改名
    private String memberName;    // 中奖人名 (作为冗余展示用)

    // ================== 4. 资产明细信息 ==================
    private String prizeType;     // 奖品类型 (SCORE, BALANCE, COUPON)
    private String prizeCode;     // 奖品code (具体发哪种券、哪个sku)
    private String prizeValue;    // 奖品值 (发多少钱/多少分)
    private Integer prizeLevel;   // 奖励等级
    private String prizeName;     // 奖品名称
    /**
     * 过期时间
     */
    private LocalDateTime validUntil;

}