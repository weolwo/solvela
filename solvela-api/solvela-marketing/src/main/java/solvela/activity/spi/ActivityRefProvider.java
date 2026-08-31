package solvela.activity.spi;

import solvela.activity.domain.dto.ActivityRefItem;
import solvela.enums.ActivityTypeEnum;

import java.util.List;

/**
 * 活动下游引用的查询契约，由各玩法模块实现。
 *
 * <h3>为什么要有这个接口</h3>
 * 三个玩法表（t_prize_pool_config / t_task_config / t_lottery_config）的 Dao 都在 solvela-marketing，
 * 而本类所在的 common-api 是 solvela-marketing 的<b>上游</b> —— common-api 直接引用它们会造成循环依赖。
 * 故用依赖倒置：<b>common-api 定契约，solvela-marketing 实现并注册为 Bean</b>。
 * 新增第四种玩法时只需加一个实现类，common-api 一行不用改。
 *
 * <h3>刻意不用 Map&lt;ActivityTypeEnum, Provider&gt; 装配</h3>
 * 本项目已因「枚举键 Map 用字符串 get」踩过三次坑（GlobalEventDispatcher /
 * PrizeStrategyFactory / AssetStrategyFactory），且三次都是编译通过、运行恒 null 的静默失效。
 * 这里改用 List 注入 + 各实现自报 {@link #supportType()}，从根上绕开那类缺陷。
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
public interface ActivityRefProvider {

    /**
     * 本实现负责哪种玩法
     */
    ActivityTypeEnum supportType();

    /**
     * 统计该活动在本玩法下的引用明细，无引用时返回空列表。
     * 用于删除守卫 —— 有引用就不许删，并把明细展示给运营。
     */
    List<ActivityRefItem> countRefs(String activityCode);

    /**
     * 该活动挂了几个「玩法主体」（抽奖=奖池数 / 任务=任务配置数 / 彩票=彩票玩法数）。
     *
     * <p>与 {@link #countRefs} 的区别：countRefs 是<b>删除守卫</b>用的，把物资、期号这类
     * 附属引用也算进去（它们同样会被删成孤儿）；本方法只回答「这个活动配了几个玩法」，
     * 是给统计与大屏展示用的一个数。
     *
     * <p>刻意做成 SPI 的一个方法，而不是在统计 SQL 里写三个子查询硬编码三张表 ——
     * 后者每加一种玩法都要改统计代码，正是这个 SPI 当初要消灭的东西。
     */
    long gameplayCount(String activityCode);

    /**
     * 该活动的玩法是否已配置完备（判据 = 能不能上线）。
     *
     * <p><b>返回 null 表示已完备</b>，返回非 null 时是「还差什么」的人话说明。
     *
     * <p>⚠️ 判据必须是「能不能上线」，不能是「下游表有没有记录」：
     * t_lottery_config 完全可以在没有任何奖级规则的情况下保存成功
     * （LotteryConfigService.online() 正是为此在上线前拦了一道），
     * 按「有无记录」判会把一个上不了线的半残废状态显示成「已配置」，比不显示更糟。
     *
     * <p>实现时应<b>复用该玩法既有的上线前校验</b>，不要另写一套 ——
     * 本项目已经吃过同一规则两处实现漂移的亏（TicketMatcher 与结算 SQL 守卫，
     * SettleSemanticsTest 就是专门防它们漂移的）。
     */
    String checkConfigured(String activityCode);

    /**
     * 把该活动在本玩法下的配置<b>复制</b>到新活动。
     *
     * <h3>为什么复制也走这个 SPI</h3>
     * 与 {@link #countRefs} 同一个理由：三张玩法表的 Dao 都在 solvela-marketing，
     * 而活动模块是它的上游。硬写成「活动模块里 if 三种类型各查一张表」既造成循环依赖，
     * 又让每加一种玩法都得回头改活动模块。
     *
     * <h3>实现必须遵守的三条</h3>
     * <ol>
     *   <li><b>所有唯一编码重新发</b>（pool_code / lottery_code 等）。抄过来直接撞唯一索引；</li>
     *   <li><b>运行态计数器归零</b>：{@code used_stock}、{@code version}、{@code sold_count} 这类。
     *       抄过来等于新活动开局库存就少一半，而这件事在页面上完全看不出来；</li>
     *   <li><b>玩法主体一律落成「不可运行」状态</b>（彩票下线 / 任务待生效 / 奖池按需）。
     *       复制出来的活动本身是「未开始」，玩法却是上线的话，会出现活动没开始但已经能领号的情况 ——
     *       {@code TicketIssueService} 只校验彩票玩法的状态，<b>不看活动状态</b>。</li>
     * </ol>
     *
     * <p>运行态数据（中奖流水、任务记录、期号与已售号码）一条都不能带。
     *
     * @param sourceActivityCode 源活动编码
     * @param targetActivityCode 新活动编码，调用方已建好活动主记录
     * @param prizeCodeMap       旧奖品编码 -&gt; 新奖品编码。奖品配置由活动模块先复制完再调本方法，
     *                           玩法侧凡是引用 prize_code 的地方都要按它重映射，
     *                           否则新活动的奖池会指向老活动的奖品
     */
    void copyTo(String sourceActivityCode, String targetActivityCode, java.util.Map<String, String> prizeCodeMap);
}
