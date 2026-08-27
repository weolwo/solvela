package solvela.lottery.runtime.domain;

import lombok.Data;

/**
 * 领号入参（HTTP 侧）。
 *
 * Service 的方法签名用的是平铺参数而不是本对象，理由是规则脚本里
 * {@code obtainTicket(code, issueNo, member)} 比先 new 再 setter 可读得多
 * （不是因为 QLExpress 传不了对象 —— 它可以）。
 * 本 Form 只负责把 HTTP 请求体转成那几个参数，顺带用注解做一层基础校验。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Data
public class TicketObtainCommand {

    /** 彩票编码 */
    private String lotteryCode;

    /** 期号 */
    private String issueNo;

    /**
     * 会员号（关联键）。v3.71.0 之前这里收的是账号 —— 账号可改，改完这个人
     * 已领的号码就查不出来了，而且不报错。展示用的账号由服务端查会员表取。
     */
    private Long memberId;

    /**
     * 幂等键。调用方传了才启用防重 —— 这是「网络重试防重」，
     * 与业务限购是两回事（限购由上游负责，本模块允许同一用户领任意多张）
     */
    private String requestId;
}
