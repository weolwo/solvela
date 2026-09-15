package solvela.notification.domain;

import solvela.enums.NotificationCategoryEnum;
import solvela.enums.NotificationTemplateEnum;

/**
 * 一条已经渲染完、随时可以送出去的通知。编排层交给渠道的就是它。
 *
 * <p>注意它<b>同时</b>带着「渲染后的 {@link #title} / {@link #summary}」和
 * 「原始的 {@link #templateCode} / {@link #templateVersion} / {@link #paramsJson}」——
 * 两份看着冗余，但用途完全不同：
 *
 * <ul>
 *   <li>站内信渠道<b>只落后者</b>（模板引用 + 参数），正文读的时候现渲染。
 *       这是那张 3 亿行的表能瘦下来的原因。</li>
 *   <li>短信 / Push 渠道<b>只要前者</b>：它们把文本交给外部服务就结束了，
 *       没有「以后再渲染一次」这回事。</li>
 * </ul>
 *
 * <p>{@link #summary} 是<b>发送时就渲染好</b>的那 128 字，站内信会把它落库 ——
 * 收件箱列表页直接读这一列，零渲染、零查模板表。高频路径不碰模板。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
public record RenderedNotification(
        Long memberId,
        NotificationTemplateEnum template,
        String templateCode,
        Integer templateVersion,
        NotificationCategoryEnum category,
        String title,
        String content,
        String summary,
        String paramsJson,
        String bizRefId,
        /** 人工发送的操作人，落进 create_by；系统发送为 null */
        String operator
) {
}
