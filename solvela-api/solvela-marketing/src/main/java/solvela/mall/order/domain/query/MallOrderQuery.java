package solvela.mall.order.domain.query;

import solvela.base.domain.PageParam;
import lombok.Data;

import java.time.LocalDate;
import lombok.EqualsAndHashCode;

/**
 * 商城订单分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallOrderQuery extends PageParam {

    /** 订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键 */
    private String orderNo;

    /**
     * 会员号 —— <b>关联键，查询走它</b>（走 idx_mall_ord_member）。
     * 前端传的是账号，由 Service 经 MemberService 换成会员号再查。
     */
    private Long memberId;

    /**
     * 账号。🔴 <b>不是查询条件</b>，只是给前端填的入口 ——
     * DDL 注释原文写着「展示快照，非关联键，不要用于查询」：这一列身上没有任何索引，
     * 写 {@code WHERE member_name = ?} 就是全表扫；给它建索引更不行，
     * 关联键会就此悄悄退回 member_name，改名断链的问题原样复活。
     *
     * <p>Service 会先把它换成 {@link #memberId} 再查，换不到就直接返回空结果
     * （查一个不存在的账号，正确答案就是「没有订单」，不是「全部订单」）。
     */
    private String memberName;

    /** 商品编码（跨环境稳定的那个） */
    private String commodityCode;

    /** 商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它 */
    private String commodityType;

    /** 状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败 */
    private Integer status;

    /** 订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次 */
    private String sourceType;

    /** 商品名称：模糊匹配（订单里的快照） */
    private String commodityName;

    /** 创建时间起 */
    private LocalDate createTimeBegin;

    /** 创建时间止 */
    private LocalDate createTimeEnd;

    /**
     * 排行榜取多少条。只对统计接口有意义，列表接口忽略它。
     */
    private Integer rankTopN;

}
