package solvela.member.api;

import java.time.LocalDateTime;

/**
 * 一张实物履约单，给<b>本人</b>看的形状。
 *
 * <h3>🔴 收件电话是脱敏值</h3>
 * 口径与 {@code MallAddressView} 完全一致（{@code 138****8000}）：解密后在应用层截，
 * 不在库里存第二份脱敏值 —— 存两份就又回到「同一份个人信息存两处」。
 *
 * <p>为什么本人看自己的地址也要脱敏：这个返回会经过网关、进浏览器、留在
 * 各级日志和抓包里。脱敏的成本是零，而「完整手机号在一条 JSON 里飘一路」
 * 的代价要等到出事那天才结算。姓名与地址不脱敏 —— 用户得能认出这是哪一单。
 *
 * @param deliveryId  履约单 id，补填收件信息时要带回来
 * @param prizeName   奖品名。由上游单据带下来，可能为空
 * @param sourceType  来源：{@code PROPOSAL}-中奖 / {@code MALL}-商城兑换
 * @param sourceBizId 来源单号：提案 id 或商城订单号。客诉时靠它对上游
 * @param status      状态：-1 已取消 / 0 待发货 / 1 已发货 / 2 已签收 / 3 异常退回
 * @param statusText  给用户看的那句话。<b>由服务端给</b> —— 端上做映射表就是第二份状态机
 * @param needAddress 还差收件信息。<b>这是「去填地址」按钮唯一的判据</b>，
 *                    端上不要自己按 status 推：什么时候还能填是状态机的一部分，
 *                    各端各推一份的话，状态机改一次就会有一个端开始给出错的按钮
 * @param receiverName    收件人。没填过是 null
 * @param receiverPhone   收件电话，<b>脱敏值</b>。没填过是 null
 * @param receiverAddress 收件地址。没填过是 null
 * @param logisticsCompany 物流公司。未发货是 null
 * @param logisticsNo      物流单号。未发货是 null
 * @param createTime       下单/中奖时间
 * @author alaric
 * @date 2026-09-18
 */
public record MemberDeliveryView(
        Long deliveryId,
        String prizeName,
        String sourceType,
        String sourceBizId,
        Integer status,
        String statusText,
        boolean needAddress,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String logisticsCompany,
        String logisticsNo,
        LocalDateTime createTime) {
}
