package solvela.admin.module.notification.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 人工发送站内信的入参。
 *
 * <p>收件人给两种填法是因为<b>客服手上通常只有账号</b>，而运营从报表里导出来的是会员号。
 * 两个都收，服务端合并 —— 让页面上只有一个输入框、逼客服先去查会员号，是把
 * 内部实现的别扭转嫁给使用的人。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Data
public class ManualNotifyRequest {

    @Schema(description = "收件人会员号。与 memberNames 二选一或都填，服务端合并去重")
    private List<Long> memberIds;

    @Schema(description = "收件人会员账号。查不到会整批拒绝，不是跳过")
    private List<String> memberNames;

    @Schema(description = "模板编码。不填默认 MANUAL（自定义文本）")
    private String templateCode;

    @Schema(description = "渲染参数。MANUAL 模板要 title 与 content 两个键")
    private Map<String, Object> params;

    @Schema(description = "关联业务单号，可空。客服一般填工单号，方便事后对账")
    private String bizRefId;
}
