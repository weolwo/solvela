package solvela.admin.module.system.datatracer.constant;


import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 数据业务类型
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-07-23 19:38:52-
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@AllArgsConstructor
@Getter
public enum DataTracerTypeEnum implements BaseEnum {

    /**
     * 商品
     */
    GOODS(1, "商品"),

    /**
     *通知公告
     */
    OA_NOTICE(2, "OA-通知公告"),

    /**
     * 企业信息
     */
    OA_ENTERPRISE(3, "OA-企业信息"),

    /**
     * 定时任务配置变更。
     *
     * <p>🔴 定时任务能触发发奖与结算，改 cron、改参数、启停都属高危操作，
     * <b>改动无痕是不可接受的</b>。复用本模块而不是新建审计表 ——
     * 能力已经有了，新建等于多一份要维护的东西。
     */
    SOLVELA_JOB(10, "定时任务"),

    /**
     * 登录风控规则的变更。
     *
     * <p>🔴 与定时任务同一个理由，而且更直接：<b>切一次档位影响的是所有用户</b>。
     * 把一条规则从陪跑切到生效，下一秒起所有命中它的人都要多验一道码；
     * 把全局滑块关掉，所有人都不用拖图了。
     *
     * <p>而「上周为什么突然人人都要验证码」这个问题，没有变更记录就只能靠猜 ——
     * 规则参数在表里、能在后台改，正是为了不发版就能调，代价就是必须留痕。
     */
    MEMBER_RISK_RULE(11, "登录风控规则"),

    /**
     * 登录风控名单（黑 / 灰 / 白）。
     *
     * <p>🔴 一条 IP 黑名单可能把一整个公司出口挡在外面，而「上周是谁加的、为什么」
     * 是那时唯一能回答问题的东西。表里的 {@code operator} / {@code revoke_reason} 也记着，
     * 这里再留一份是为了和其它变更放在同一条时间线上看。
     */
    MEMBER_RISK_LIST(12, "登录风控名单"),

    ;

    private final Integer value;

    private final String desc;
}
