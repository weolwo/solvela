package solvela.notification.domain.command;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 人工发送一条站内信。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Data
public class ManualNotifyCommand {

    /**
     * 收件人会员号。
     *
     * <p>🔴 <b>有硬上限</b>（见 {@code NotificationAdminService.MANUAL_MAX_RECIPIENTS}）。
     * 不是怕慢，是怕这个入口变成「用写扩散做广播」的后门 —— 一次贴 10 万个会员号，
     * 就是整套设计最想避免的那件事，而且它还绕过了公告的免打扰与人群规则。
     */
    private List<Long> memberIds;

    /**
     * 模板编码。不填默认 {@code MANUAL}（自定义文本）。
     *
     * <p>填别的模板编码时，{@link #params} 要按那个模板的占位符给 ——
     * 客服要补发一条「中奖通知」就是这么用的。
     */
    private String templateCode;

    /**
     * 渲染参数。
     *
     * <p>{@code MANUAL} 模板要 {@code title} 与 {@code content} 两个键，
     * 也就是正文由发送方现填。其它模板按各自的占位符给。
     */
    private Map<String, Object> params;

    /** 关联业务单号，可空。客服一般填工单号，方便事后对账 */
    private String bizRefId;
}
