package solvela.admin.module.ledger.logistic.domain.vo;

import solvela.enums.DeliveryStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 发货物流表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
public class PhysicalDeliveryVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    @Schema(description = "会员账号（下单当时的快照）")
    private String memberName;

    @Schema(description = "来源单号：PROPOSAL 存提案ID / MALL 存订单号")
    private String sourceBizId;

    @Schema(description = "来源类型：PROPOSAL / MALL")
    private String sourceType;

    /*
     * 收件三项库里是密文，这里是<b>解密后的明文</b>（resultMap 上挂了 PiiTypeHandler）。
     *
     * ⚠️ 刻意<b>不脱敏</b>：运营台这个页面的用途就是发货 —— 看不到完整地址就没法干活，
     * 也没法把单子导给物流商。加密防的是<b>静态泄露</b>（库被脱、备份被拷、DBA 直接 select），
     * 不是防有应用权限的人。
     * 「谁能看到完整收件信息」是权限点的问题，不是加密能解决的 —— 那件事还没做，
     * 见交接文档 §13.8。
     */
    @Schema(description = "收件人姓名（库中密文，接口返回明文）")
    private String receiverName;

    @Schema(description = "收件人电话（库中密文，接口返回明文）")
    private String receiverPhone;

    @Schema(description = "收件详细地址（库中密文，接口返回明文）")
    private String receiverAddress;

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回")
    private DeliveryStatusEnum status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
