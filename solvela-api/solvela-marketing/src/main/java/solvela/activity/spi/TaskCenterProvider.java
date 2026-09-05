package solvela.activity.spi;

import solvela.enums.ActivityTypeEnum;
import solvela.marketing.api.TaskCenterItem;

import java.util.List;

/**
 * 任务中心的查询契约，由任务玩法模块实现。
 *
 * <h3>为什么要有这个接口</h3>
 * 任务的表与运行态都在 {@code solvela.task} 下，而活动域（{@code solvela.activity}）
 * 是它的<b>上游</b> —— {@code ActivityFacade} 直接引用任务的 service 会造成反向依赖。
 * 故用依赖倒置：<b>活动域定契约，玩法模块实现并注册为 Bean</b>。
 * 形状对齐 {@link ActivityRefProvider}，那是本仓这类 SPI 的样板。
 *
 * <p>{@code ActivityApi} 的类注释里写死了这条：「🔴 跨玩法的方法必须走 SPI，
 * 不许在活动域里写 if-else」。今天只有 TASK 一种实现，但把 if-else 写进活动域的代价
 * 不是今天付，是第二种玩法要出任务中心时才付 —— 那时改的是活动域，而它本不该知道玩法。
 *
 * <h3>🔴 用 supportType 自报，不要 Map&lt;ActivityTypeEnum, Provider&gt;</h3>
 * {@link ActivityRefProvider} 的注释里记着：本项目已因「枚举键 Map 用字符串 get」
 * 踩过三次坑（GlobalEventDispatcher / PrizeStrategyFactory / AssetStrategyFactory），
 * 三次都是<b>编译通过、运行恒 null 的静默失效</b>。这里沿用 List 注入 + 自报家门。
 *
 * @Date 2026-09-05
 */
public interface TaskCenterProvider {

    /**
     * 本实现负责哪种玩法
     */
    ActivityTypeEnum supportType();

    /**
     * 某个会员在某个活动下的任务中心。
     *
     * <p>返回<b>该活动全部启用中的任务</b>，会员没做过的也要给 —— 任务中心的价值
     * 恰恰是「告诉用户还有什么可做」。没有进度记录的任务，
     * {@link TaskCenterItem#current} 为 0、{@link TaskCenterItem#status} 为 null。
     *
     * <p>活动不存在、或该活动没有任务时返回<b>空列表，不是 null</b>。
     */
    List<TaskCenterItem> listTasks(String activityCode, Long memberId);
}
