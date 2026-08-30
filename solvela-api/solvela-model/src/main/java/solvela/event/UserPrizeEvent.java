package solvela.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import solvela.enums.EventCategoryEnum;

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

    /**
     * 玩法类型 BASIC/DRAW/TASK/LOTTERY。
     *
     * <p><b>发送方必须填</b>：派发方据它归类提案来源，而拆成独立服务后它<b>没法回头查活动表</b>
     * —— 活动配置在营销服务，派发在会员服务。为空时派发方降级为 MANUAL，不中断发奖。
     */
    private String activityType;

    // ================== 3. 用户信息 ==================
    /**
     * 会员号 —— <b>关联键</b>。下游（发奖流水/提案/钱包/券/履约单）全部按它落库。
     * v3.71.0 之前这个字段一直没人赋值，真正在用的是下面那个 memberName；换键之后反过来。
     */
    private Long memberId;
    /**
     * 账号 —— <b>展示快照</b>，随事件一起下传，供单据落「当时是谁」。
     * 🔴 不要拿它做任何关联或判等：它可改，改完就对不上了。
     */
    private String memberName;

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