package net.lab1024.sa.base.module.support.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * job 任务触发类型。
 *
 * <p>🔴 <b>{@code FIXED_DELAY} 已在 v3.58.0 删除。</b> 它与抢占式调度概念上不相容：
 * 该类型的语义是「上一次<b>执行结束</b>后再等 N 秒」，而抢占发生在任务<b>开始之前</b> ——
 * 那一刻根本不知道它什么时候结束，算不出真正的下一次触发时间。
 *
 * <p>硬留下来只有两条路，都不可接受：
 * <ul>
 *   <li>按「触发时刻 + N 秒」算 → 产出的其实是 FIXED_RATE。
 *       名字没变、配置没变、日志没变，<b>语义悄悄换了</b>；</li>
 *   <li>抢占时把 {@code next_trigger_time} 推到 2099、执行完回填 →
 *       会让调度器自监控瞎掉（判据是 {@code next_trigger_time < now - 5min}，
 *       而 2099 永远不满足）。节点在执行中崩溃、回填没跑到，
 *       任务就<b>永久死亡且不触发任何告警</b>。</li>
 * </ul>
 *
 * <p>代价接近零：{@link #CRON} 支持秒字段（{@code * /10 * * * * *} 即每 10 秒），
 * 短周期场景完全覆盖；而「不许与上一次重叠」本就是 {@link SmartJobBlockStrategyEnum}
 * 的职责，不该由触发类型来表达 —— <b>一个语义只在一处表达</b>。
 *
 * @author huke
 * @date 2024年6月29日
 **/
@AllArgsConstructor
@Getter
public enum SmartJobTriggerTypeEnum implements BaseEnum {

    /**
     * cron 表达式，六段式（秒 分 时 日 月 周）
     */
    CRON("cron", "cron表达式"),

    /**
     * 一次性到点触发：执行完置 {@code terminal_flag = 1}，不再计算下次。
     *
     * <p>活动场景的刚需 ——「活动 8/20 00:00 开始」「结束后 7 天清理临时数据」
     * 这类都是一次性时间点，用 cron 表达会退化成「每分钟扫一遍所有活动看有没有到点的」，
     * 几百个活动之后那个扫描本身就是性能问题，且延迟精度与扫描频率死死绑在一起。
     *
     * <p>🔴 <b>它强制豁免 jitter。</b>「活动 00:00 开始」是<b>业务时间点</b>而不是调度时间点，
     * 打散 60 秒等于活动晚开一分钟。
     */
    ONE_TIME("one_time", "一次性"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;
}
