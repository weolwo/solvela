package sa.lottery.runtime.domain;

/**
 * 领号结果
 *
 * @param lotteryCode  彩票编码
 * @param issueNo      期号
 * @param ticketNumber 票号（用户看到的号码）
 * @param sequenceNo   FPE 算号游标（0-indexed）。对外可以不展示，但对账要用
 * @param securitySign 防篡改签名
 * @param obtainTime   领号时间，字符串形式便于脚本引擎与前端直接消费
 *
 * @Author alaric
 * @Date 2026-07-28
 */
public record TicketObtainVO(String lotteryCode,
                             String issueNo,
                             String ticketNumber,
                             long sequenceNo,
                             String securitySign,
                             String obtainTime) {
}
