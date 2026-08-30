package solvela.member.api;

import java.io.Serializable;

/**
 * 资产入账结果：会员服务处理完之后<b>回写给营销服务</b>的消息。
 *
 * <h3>为什么这一段必须异步</h3>
 * 提案可能进人工审批池，几小时后才有结论；即便自动通过，资产入账也在提案事务提交之后。
 * 同步调用等不了这么久，所以发奖被切成两段：
 * <pre>
 *   同步 HTTP：受理 / 拒绝 + 原因   → 落 proposal_status
 *   异步消息：入账成功 / 失败       → 落 status（终态）  ← 本消息
 * </pre>
 *
 * <h3>为什么不是会员服务直接改营销的表</h3>
 * 2026-08-30 之前就是那么干的（{@code AssetDispatchEngine} 直接 update
 * {@code t_prize_log}），那段代码自己的注释都写着「分层上略有妥协」。
 * 两个服务之后这条路不通了：一个服务不能写另一个服务的表 ——
 * 不是洁癖，是拆库那天它会变成一个查不出来的空指针。
 *
 * @param messageId  消息唯一标识，消费方据它幂等（{@code t_mq_message_log.message_id}）
 * @param sourceBizId 来源单号，与 {@code t_prize_log.external_biz_no} 同值 —— 消费方靠它定位那一行
 * @param proposalId 提案 id，人工排查用
 * @param success    资产是否真的入账了
 * @param failReason 失败原因。<b>会落进发奖流水并可能展示给用户</b>，别放表名、堆栈
 */
public record PrizeDispatchResultMessage(
        String messageId,
        String sourceBizId,
        Long proposalId,
        boolean success,
        String failReason) implements Serializable {
}
