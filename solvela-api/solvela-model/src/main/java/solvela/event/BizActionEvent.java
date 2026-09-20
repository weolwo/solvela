package solvela.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 业务动作事件 —— <b>"平台里发生了一件事"</b>，仅此而已。
 *
 * <h3>它不是"任务事件"</h3>
 * 发布方（商城、会员、外部场景）<b>不知道有任务系统存在</b>，只是把一件既成事实广播出去。
 * 今天唯一的订阅者是任务引擎（{@code solvela.task.adapter.BizActionEventListener}），
 * 将来会有数据统计、风控、等级体系 —— 那时发布方<b>一行都不用改</b>。
 *
 * <p>判据（方案 §2.4）：把 {@code solvela-marketing} 从 classpath 上摘掉，
 * {@code solvela-mall} 仍然编译得过、下单仍然跑得通。事件发出去没人接，仅此而已。
 *
 * <h3>🔴 刻意<b>不</b>继承 {@link BaseBizEvent}</h3>
 * {@code GlobalEventDispatcher.dispatch} 的签名是 {@code dispatch(BaseBizEvent)} ——
 * 它监听<b>父类</b>，所以进程里发布的任何 {@code BaseBizEvent} 子类都会被它接走。继承的后果：
 * <ul>
 *   <li><b>没配路由时</b>：每一条业务动作都会打一行「未找到分类 [x] 的处理器」的 warn。
 *       下单量有多大，日志就有多脏；</li>
 *   <li><b>配了路由时更糟</b>：事件会被丢进 {@code solvela-async-executor}，
 *       而那个池<b>队列无界</b>（{@code AsyncConfig} 没调 {@code setQueueCapacity}，
 *       默认 {@code Integer.MAX_VALUE}），积压的后果不是降级是 OOM；
 *       而且那个池<b>正被派奖链路占用</b> —— 一次下单洪峰会让派奖排在后面，
 *       表现是「抽奖中奖了但积分半天不到账」，两条链路看起来毫无关系，根因极难联想。
 *       这段论证的原文在 {@code TaskEventExecutorConfig} 的类注释里。</li>
 * </ul>
 * 所以本类是<b>独立类型</b>，有自己的监听器，只经过一个<b>有界</b>队列。
 *
 * <h3>🔴 一个通用事件，不是一个业务一个类</h3>
 * {@code t_task_event.payload_schema} 那一列说明这个项目<b>已经选了「事件的 schema 是数据，
 * 不是类」</b>。一个事件一个类的代价是：每加一个打点都要动 solvela-model，
 * 而它是全仓所有模块的共同依赖 —— 改它等于全量重编译，而且会让人误以为加打点是件大事。
 *
 * <p>通用事件下，加一个打点 = <b>生产者本地一行 publish + 后台 {@code t_task_event} 加一行</b>，
 * 不动任何共享模块。
 *
 * <h3>🔴 负载里只装领域既成事实</h3>
 * 可以有 {@code memberId} / {@code bizId} / {@code amount}；
 * <b>不许</b>出现 {@code taskCode} / {@code progress} / {@code taskRecordId} 这类字段 ——
 * 一旦出现，商城就开始替任务系统记账了，上面那条判据当场不成立。
 *
 * @param actionCode  业务动作编码：{@code ORDER_PAID} / {@code MEMBER_REGISTER} /
 *                    {@code DAILY_SIGN} …… 它描述的是<b>发布方自己发生的事</b>。
 *                    任务注册表恰好用同一个字符串订阅它，这不让它变成任务术语 ——
 *                    摘掉 marketing，{@code ORDER_PAID} 在商城里依然有意义
 * @param memberId    会员号，关联键。<b>必填</b>
 * @param bizId       天然业务单号，用作幂等键（订单号 / 外部单号 / 会员号）。
 *                    🔴 有单号的动作<b>必须传</b>：{@code t_task_event.biz_id_required}
 *                    会在下游强制校验，缺了当场拒绝并落丢弃流水。
 *                    天然没有单号的（签到）传 null，由服务端按事件自然日兜底
 * @param amount      计量值（实付金额等），计次型动作传 null
 * @param occurredAt  事件<b>实际发生</b>的时间，不是被处理的时间。
 *                    下游按它归属周期 —— 迟到的事件应当归属它发生的那一天
 * @param payload     事件原文，落进下游流水供客诉复盘。可空
 * @author alaric
 * @date 2026-09-17
 */
public record BizActionEvent(
        String actionCode,
        Long memberId,
        String bizId,
        BigDecimal amount,
        LocalDateTime occurredAt,
        Map<String, Object> payload) {

    /**
     * 紧凑构造器：把不变量钉在类型上。
     *
     * <p>🔴 {@code occurredAt} 在这里兜底而不是留给每个发布方，理由和
     * {@code TaskEventContext} 的紧凑构造器一样 —— 那边的注释记着这个模式
     * 「在本项目已经复发到第 6 次」。发布方漏填一个时间就该由类型本身接住，
     * 而不是等它一路走到下游的 NOT NULL 列上炸出一句看不出根因的 SQL 错误。
     */
    public BizActionEvent {
        if (actionCode == null || actionCode.isBlank()) {
            throw new IllegalArgumentException("actionCode 不能为空：下游按它找订阅者");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("memberId 不能为空：它是全链路关联键");
        }
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** 计次型动作：没有金额 */
    public static BizActionEvent of(String actionCode, Long memberId, String bizId) {
        return new BizActionEvent(actionCode, memberId, bizId, null, LocalDateTime.now(), Map.of());
    }
}
