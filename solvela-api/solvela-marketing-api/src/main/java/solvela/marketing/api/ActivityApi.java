package solvela.marketing.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

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
 *   listOpenActivities()           C 端可见的活动列表                 ✅ 已实现
 *   draw(cmd)                      抽奖（走脚本编排）                 ✅ 已实现
 *   claimPrize(cmd)                领取奖励                           ⏳
 *   getPrizeRecord(qry)            我的奖励：可领的 + 历史记录          ⏳
 *   getTaskCenter(code, memberId)  我的任务中心                       ✅ 已实现
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
     * 当前对 C 端可见的活动列表：<b>上线中</b>且还没结束的。
     *
     * <p>包含<b>未开始</b>的活动 —— C 端要把它们显示成「即将开始」，
     * 用户看到预告才会记着回来。是否此刻可参与由 {@link ActivityBriefView#joinable} 判，
     * 不要在这里过滤掉。
     *
     * <p>已下线（OFFLINE）与已结束的<b>不出现</b>：那是运营主动关掉或时间到了的活动，
     * 出现在列表里只会让用户点进去看一个空页面。
     *
     * <p>排序由域决定（运营排序权重 + 开始时间），网关不重排 ——
     * 重排一次就是第二份排序规则，而运营改了权重之后没人会想起来去改网关。
     *
     * <p>没有活动时返回<b>空列表，不是 null</b>：那是新环境的正常状态。
     */
    @GetExchange("/open")
    List<ActivityBriefView> listOpenActivities();

    /**
     * 某个会员在某个活动下的任务中心。
     *
     * <p>返回该活动<b>全部生效中的任务</b>，会员没做过的也给 ——
     * 任务中心要回答的是「还有什么可做」，只回有进度的等于第一次进来看空页面。
     *
     * <p>活动不存在、不是任务型、或没配任务，一律返回<b>空列表</b>而不是抛异常：
     * 它们对用户是同一件事（这儿没有任务），而抛出去跨进程后会变成 5xx。
     *
     * <p>🔴 <b>没有「领取」这一步</b>：任务达标时自动发奖
     *（{@code TaskPrizeDispatcher.dispatchReachedStages}），
     * {@code TaskRecordStatusEnum} 里也没有 CLAIMED。见 {@link TaskCenterItem} 的类注释。
     *
     * <p>实现走 {@code TaskCenterProvider} SPI —— 活动域是玩法的上游，不能反向依赖。
     */
    @GetExchange("/{activityCode}/task")
    List<TaskCenterItem> getTaskCenter(@PathVariable String activityCode,
                                       @RequestParam Long memberId);

    /**
     * 我的全部任务：当前可见的<b>任务型活动</b>下的任务，合成一份。
     *
     * <p>C 端的「任务中心」是一个独立 tab，没有活动上下文 —— 用户不关心
     * 「这个任务属于哪场活动」，他只想知道还有什么可做。
     *
     * <p>为什么不让网关自己循环调 {@link #getTaskCenter}：那是<b>跨进程</b>的 N+1，
     * 三个任务活动就是三次 HTTP。这里在营销服务<b>进程内</b>循环，
     * 每个活动内部的四类查询本来就是批量的。
     *
     * <p>没有任务型活动时返回空列表。
     */
    @GetExchange("/task/mine")
    List<TaskCenterItem> getMyTasks(@RequestParam Long memberId);

    /**
     * 参与一次（抽奖）。
     *
     * <p>链路：<b>活动校验（Java）→ 玩法编排脚本 → 脚本调用抽奖函数 → 结果原样返回</b>。
     * 奖池由脚本决定，调用方不传也传不了，见 {@link ActivityDrawCmd}。
     *
     * <p>三种结果都由返回值表达，见 {@link DrawRejectReason}：中奖 / 没中奖 / 没被受理。
     * 活动不存在、活动不在参与窗内、没挂编排脚本，都算<b>没被受理</b>，不抛异常 ——
     * 它们全是预期内的情况，抛出去跨进程后一律变成 5xx。
     *
     * <p>连抽：{@code cmd.times()} 是客户端的<b>意愿</b>，真正抽几次由编排脚本裁决 ——
     * 脚本调 {@code draw_executeMultiDrawByScript(pool, n)} 时给的那个 n 才算数。
     */
    @PostExchange("/draw")
    DrawResultView draw(@RequestBody ActivityDrawCmd cmd);
}
