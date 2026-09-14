package solvela.ledger.coupon.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「某个会员有几张券快过期了」—— 按会员聚合的一行。
 *
 * <p>🔴 这个形状本身就是 §8「合并同类项」那条规则的载体：它只带<b>张数</b>和
 * <b>最近一张的失效时间</b>，<b>没有券列表</b>。所以拿着它的人写不出
 * 「遍历券、一张发一条」那种代码 —— 规则由类型挡着，不靠自觉。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Data
public class MemberCouponExpiringDTO {

    private Long memberId;

    /** 即将过期的张数 */
    private Integer count;

    /** 其中最早失效的那张的时间。文案里的「最近一张将于 X 失效」用它 */
    private LocalDateTime nearestExpireTime;
}
