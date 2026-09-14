package solvela.notification.domain;

import solvela.enums.NotificationTemplateEnum;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次「通知请求」。
 *
 * <h3>🔴 业务方发的是请求，不是「插入一条站内信」</h3>
 * <pre>
 * ✅ notificationService.send(NotifyRequest.of(PRIZE_WON, memberId).param("prizeName", name).build());
 * ❌ memberNotificationDao.insert(memberId, title, content);
 * </pre>
 *
 * <p>这个差别<b>今天是零成本的</b>（实现里就是往 {@code t_member_notification} 插一行），
 * 但它决定了将来加 App Push / 短信时，是「注册一个 {@code NotificationSender}」
 * 还是「重写所有调用点」。
 *
 * <p>同一个业务事件（中奖）迟早要同时走站内信 + Push + 短信。站内信如果自成一套，
 * 加 Push 时就得把事件订阅再写一遍，而且两边的免打扰、频控规则会分叉 ——
 * 分叉之后就再也合不回去了，因为两边都有了各自的存量行为。
 *
 * @param template  模板。用枚举而不是裸字符串：拼错在编译期就挂，不会等到运行时静默不发
 * @param memberId  收件人会员号
 * @param params    渲染参数。🔴 只放<b>显示值</b>，不要放 id（理由见 {@code MemberNotification#params}）
 * @param bizRefId  关联业务单号，可空。纯排查用 —— 用户说「我没收到」时拿它对账
 *
 * @Author alaric
 * @Date 2026-09-14
 */
public record NotifyRequest(
        NotificationTemplateEnum template,
        Long memberId,
        Map<String, Object> params,
        String bizRefId
) {

    public NotifyRequest {
        if (template == null) {
            throw new IllegalArgumentException("模板不能为空");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("会员号不能为空");
        }
        // 防御性拷贝 + 不可变：请求对象会被传进编排层、再传给每个渠道，
        // 任何一个渠道往 params 里塞东西都会影响别的渠道
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static Builder of(NotificationTemplateEnum template, Long memberId) {
        return new Builder(template, memberId);
    }

    /**
     * 小 builder。不用 Lombok 的 {@code @Builder} 是因为要在 {@link #param} 上做
     * null 值的静默丢弃 —— 业务方传进来的值很多是可空的（物流单号、解冻时间），
     * 让每个调用点自己判空会写得很难看。
     *
     * <p>丢掉 null 的后果是那个占位符渲染时解析不到、<b>原样保留</b>
     * （{@code SolvelaTemplateUtil} 的口径）。所以可空参数<b>不要写进模板正文</b>，
     * 或者在这里给个兜底文案 —— 别让用户看到字面的 {@code ${logisticsNo}}。
     */
    public static final class Builder {

        private final NotificationTemplateEnum template;
        private final Long memberId;
        private final Map<String, Object> params = new LinkedHashMap<>();
        private String bizRefId;

        private Builder(NotificationTemplateEnum template, Long memberId) {
            this.template = template;
            this.memberId = memberId;
        }

        public Builder param(String key, Object value) {
            if (key != null && value != null) {
                params.put(key, value);
            }
            return this;
        }

        public Builder bizRefId(String bizRefId) {
            this.bizRefId = bizRefId;
            return this;
        }

        public NotifyRequest build() {
            return new NotifyRequest(template, memberId, params, bizRefId);
        }
    }
}
