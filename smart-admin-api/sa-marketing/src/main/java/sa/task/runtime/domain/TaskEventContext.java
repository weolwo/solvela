package sa.task.runtime.domain;

import sa.task.runtime.TaskPeriodResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 规范化后的任务事件上下文（运行态在内部只认这个对象，不认原始 Form）。
 *
 * @param eventCode  事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE ...
 * @param memberName 会员名
 * @param eventBizId 幂等键。上游有天然单号（订单号）时必须传；无单号的事件由服务端按事件日兜底，
 *                   兜底规则见 {@link sa.task.runtime.TaskPeriodResolver#resolveEventBizId}
 * @param amount     计量值，AMOUNT 类任务用；其余类型忽略。可空
 * @param eventTime  事件<b>实际发生</b>的时间（不是被处理的时间）。
 *                   周期归属按它算 —— 迟到的事件应归属它发生的那一天，而不是被处理的那一天
 * @param isNewMember 该会员是不是新会员，<b>由上游告知</b>。
 *                    营销域不拥有会员数据（库里只有钱包/流水/券，全以 member_name 字符串为键，
 *                    没有注册时间、没有会员档案），「新/老」的定义也本就属于会员域的业务概念，
 *                    不该由任务引擎去猜。null = 上游没告知 —— 此时配了人群的任务会
 *                    <b>丢弃事件并写明原因</b>，而不是假装过滤生效了
 * @param payload    事件原文，落进流水的 event_payload 供客诉复盘
 * @author alaric
 * @date 2026-08-01
 */
public record TaskEventContext(
        String eventCode,
        String memberName,
        String eventBizId,
        BigDecimal amount,
        LocalDateTime eventTime,
        Boolean isNewMember,
        Map<String, Object> payload) {

    /**
     * 紧凑构造器：把「可空」收敛在这里，后面的策略实现就不用到处判空了。
     *
     * <p>🔴 <b>eventBizId 的兜底必须在这里做，不能只放在 {@code TaskEventService.normalize()} 里。</b>
     * {@code handle(ctx)} 是对外公开的同步入口（单测与联调脚本都直接用它），
     * 绕过 normalize 传进来的 null 会一路走到 INSERT ——
     * 而 {@code t_task_record_flow.event_biz_id} 是 <b>NOT NULL 且无默认值</b>，
     * MyBatis-Plus 省略 null 字段 + MySQL 严格模式，报的是
     * {@code Field 'event_biz_id' doesn't have a default value}。
     * 这个模式在本项目已经复发到第 6 次（fail_reason -> coupon_type -> fail_reason 超长
     * -> trade_no/asset_type -> receiver_name -> 本次），
     * 把不变量钉在类型的构造器上是唯一能一次堵死的地方。
     */
    public TaskEventContext {
        if (eventTime == null) {
            throw new IllegalArgumentException("eventTime 不能为空：周期归属与幂等键兜底都依赖它");
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        amount = amount == null ? BigDecimal.ZERO : amount;
        eventBizId = TaskPeriodResolver.resolveEventBizId(eventBizId, eventTime);
    }

    /**
     * 换一个 eventBizId（服务端兜底后回填），其余不变
     */
    public TaskEventContext withEventBizId(String bizId) {
        return new TaskEventContext(eventCode, memberName, bizId, amount, eventTime, isNewMember, payload);
    }

    /**
     * 换一个金额（按事件注册表的 metric_source 从 payload 里取出来之后回填），其余不变
     */
    public TaskEventContext withAmount(BigDecimal newAmount) {
        return new TaskEventContext(eventCode, memberName, eventBizId, newAmount, eventTime, isNewMember, payload);
    }
}
