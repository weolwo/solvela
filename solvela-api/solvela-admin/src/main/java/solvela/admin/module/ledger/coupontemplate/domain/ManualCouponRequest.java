package solvela.admin.module.ledger.coupontemplate.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 人工发券的入参。
 *
 * <p>收件人给两种填法是因为<b>客服手上通常只有账号</b>，而运营从报表里导出来的是会员号。
 * 两个都收、服务端合并 —— 让页面上只有一个输入框、逼客服先去查会员号，
 * 是把内部实现的别扭转嫁给使用的人。与人工发站内信同一个做法。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Data
public class ManualCouponRequest {

    @Schema(description = "收件人会员号。与 memberNames 二选一或都填，服务端合并去重")
    private List<Long> memberIds;

    @Schema(description = "收件人会员账号。查不到会整批拒绝，不是跳过")
    private List<String> memberNames;

    @Schema(description = "券模编码。必须有启用中的模板，否则直接拒绝")
    private String couponCode;

    @Schema(description = "每人发几张，默认 1，上限 10")
    private Integer quantity;

    @Schema(description = "工单号 / 批次号。🔴 防重发的唯一依据，不能为空")
    private String bizRefId;

    @Schema(description = "发券原因。会原样显示给用户，如「就您 9 月 12 日的问题补偿」")
    private String reason;
}
