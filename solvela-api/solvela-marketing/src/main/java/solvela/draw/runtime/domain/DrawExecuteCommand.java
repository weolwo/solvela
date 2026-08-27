package solvela.draw.runtime.domain;

import lombok.Data;

/**
 * 执行抽奖 表单
 * memberId 联调期显式传入；C端接入后改为从登录态获取并删除该字段
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawExecuteCommand {

    /** 活动编码 */
    private String activityCode;

    /** 奖池编码 */
    private String poolCode;

    /**
     * 会员号（关联键）。v3.71.0 之前这里收的是账号 —— 账号可改，改完这个人的
     * 限领计数、白名单、历史流水就全对不上了，而且不报错。
     * 展示用的账号由服务端查会员表取，调用方不需要也不应该再传。
     */
    private Long memberId;

    /** 请求ID：传入则启用幂等防重（网络重试不会重复抽奖） */
    private String requestId;
}
