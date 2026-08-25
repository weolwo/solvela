package solvela.mall.order.domain.form;

import solvela.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import lombok.EqualsAndHashCode;

/**
 * 商城-兑换订单 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallOrderQueryForm extends PageParam {

    @Schema(description = "订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键")
    private String orderNo;

    /**
     * 会员号 —— <b>关联键，查询走它</b>（走 idx_mall_ord_member）。
     * 前端传的是账号，由 Service 经 MemberService 换成会员号再查。
     */
    @Schema(description = "会员号：关联键")
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
    @Schema(description = "账号：服务端换算成会员号后再查")
    private String memberName;

    @Schema(description = "商品编码（跨环境稳定的那个）")
    private String commodityCode;

    @Schema(description = "商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它")
    private String commodityType;

    @Schema(description = "状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败")
    private Integer status;

    @Schema(description = "订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次")
    private String sourceType;

    @Schema(description = "商品名称：模糊匹配（订单里的快照）")
    private String commodityName;

    @Schema(description = "创建时间起")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间止")
    private LocalDate createTimeEnd;

    /**
     * 排行榜取多少条。只对统计接口有意义，列表接口忽略它。
     */
    @Schema(description = "排行榜条数，默认 10")
    private Integer rankTopN;

}
