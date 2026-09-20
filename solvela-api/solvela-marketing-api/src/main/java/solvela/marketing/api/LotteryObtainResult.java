package solvela.marketing.api;

/**
 * 参与彩票活动的结果：拿到一个号码，或者一句「为什么没拿到」。
 *
 * <h3>为什么不复用 {@code DrawResultView}</h3>
 * 那个的形状是「抽了 N 次，每次中了什么」，而领号是<b>一次拿一个号</b>，
 * 也没有「中没中」这回事 —— 中奖要等开奖。硬套过去会得到一个
 * {@code records} 里塞着号码的抽奖结果，读的人得先知道「这其实是彩票」才看得懂。
 *
 * <h3>为什么用 message 而不是 reason 枚举</h3>
 * 领号的拒绝理由有一半来自<b>运营写的脚本</b>（单人限购、人群不符）和领号引擎
 * （售罄、停售、限流），它们是动态字符串，枚举装不下。
 * 结构性的那几种（活动没开、没挂脚本）也一并翻成人话，
 * 端上就只有一条路：把 message 显示出来。
 *
 * @param accepted     拿到号了没有
 * @param message      给用户看的一句话。<b>成功时也有</b>
 * @param lotteryCode  玩法编码。没拿到时为 null
 * @param issueNo      期号。没拿到时为 null
 * @param ticketNumber 号码。没拿到时为 null
 * @author alaric
 * @date 2026-09-18
 */
public record LotteryObtainResult(
        boolean accepted,
        String message,
        String lotteryCode,
        String issueNo,
        String ticketNumber) {

    public static LotteryObtainResult ok(String lotteryCode, String issueNo, String ticketNumber) {
        return new LotteryObtainResult(true, "号码已到账", lotteryCode, issueNo, ticketNumber);
    }

    public static LotteryObtainResult reject(String message) {
        return new LotteryObtainResult(false, message, null, null, null);
    }
}
