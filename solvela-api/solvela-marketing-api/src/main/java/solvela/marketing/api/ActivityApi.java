package solvela.marketing.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * C 端的<b>公共活动契约</b>：所有活动、所有玩法共用这一组方法。
 *
 * <h3>为什么是「公共」的</h3>
 * 抽奖、任务、彩票、商城的差异全部收在<b>脚本</b>与各玩法模块里，不体现在接口上。
 * 前端对接一次，新增第四种玩法时一行不用改 —— 这是这组 api 存在的全部意义。
 * 代价是入参里会有一个开放的 params，见 {@code PlayCmd}（随 draw 一起落地）。
 *
 * <h3>规划中的完整方法集</h3>
 * <pre>
 *   getActivityRule(code)          活动信息 + 展示配置 + 规则正文     ✅ 已实现
 *   draw(cmd)                      抽奖（走脚本编排）                 ✅ 已实现
 *   claimPrize(cmd)                领取奖励                           ⏳
 *   getPrizeRecord(qry)            我的奖励：可领的 + 历史记录          ⏳
 *   getTaskCenter(code, memberId)  我的任务中心                       ⏳
 *   getLotteryIssue(code)          当前期号                           ⏳
 *   getLotteryRecord(qry)          我的彩票记录                       ⏳
 * </pre>
 * <b>方法一个一个加，跟着实现走</b>：先把七个签名摆上来再让实现抛
 * {@code UnsupportedOperationException}，等于给前端一份会骗人的接口文档。
 *
 * <h3>🔴 跨玩法的方法必须走 SPI，不许在活动域里写 if-else</h3>
 * {@code getPrizeRecord} / {@code getTaskCenter} 天然跨 draw / task / lottery 三个模块，
 * 而活动域是它们的<b>上游</b>，不能反向依赖。照 {@code ActivityRefProvider} 的形状做依赖倒置：
 * 活动域定接口，各玩法模块实现并注册。否则每加一种玩法都要回头改这里 ——
 * 正是那个 SPI 当初要消灭的东西。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 这些端点服务于服务间调用，不该直接暴露到公网：公网入口是网关自己的
 * {@code /activity/**}，鉴权、限流、脱敏都在那一层。
 */
@HttpExchange("/internal/activity")
public interface ActivityApi {

    /**
     * 活动详情：基础信息、时间窗、状态、展示配置、规则正文一次给全。
     *
     * <p>活动不存在返回 {@code null}，不抛异常 —— C 端用户拿一个不存在的编码来访问是
     * 完全正常的事（链接过期、被人乱改地址栏）。用异常表达它，跨进程后会变成 5xx，
     * 监控上多一堆假的服务端错误。翻译成什么状态码由网关决定。
     */
    @GetExchange("/{activityCode}/rule")
    ActivityRuleView getActivityRule(@PathVariable String activityCode);

    /**
     * 参与一次（抽奖）。
     *
     * <p>链路：<b>活动校验（Java）→ 玩法编排脚本 → 脚本调用抽奖函数 → 结果原样返回</b>。
     * 奖池由脚本决定，调用方不传也传不了，见 {@link ActivityDrawCmd}。
     *
     * <p>三种结果都由返回值表达，见 {@link DrawRejectReason}：中奖 / 没中奖 / 没被受理。
     * 活动不存在、活动不在参与窗内、没挂编排脚本，都算<b>没被受理</b>，不抛异常 ——
     * 它们全是预期内的情况，抛出去跨进程后一律变成 5xx。
     */
    @PostExchange("/draw")
    DrawResultView draw(@RequestBody ActivityDrawCmd cmd);
}
