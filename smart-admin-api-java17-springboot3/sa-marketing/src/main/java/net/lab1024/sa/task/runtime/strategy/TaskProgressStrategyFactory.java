package net.lab1024.sa.task.runtime.strategy;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.task.constant.TaskTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务进度策略工厂。
 *
 * <p>🔴 <b>刻意用 {@code List} 注入 + 各实现自报 {@code supportType()}，
 * 不用 {@code Map<TaskTypeEnum, TaskProgressStrategy>}</b>（铁律 14）。
 *
 * <p>本项目已因「枚举键 Map 用字符串 get」踩过<b>三次</b>静默失效：
 * {@code GlobalEventDispatcher}（整条派奖链路从来没通过）、
 * {@code PrizeStrategyFactory}、{@code AssetStrategyFactory}。
 * 三处症状完全一样 —— {@code Map.get(Object)} 不做类型检查，
 * 用 String 去查 Enum 键的 Map <b>编译通过、运行恒 null</b>，
 * 而调用方往往还有一层 catch 兜底，于是连异常都看不到。
 *
 * <p>这里的 {@link #resolve} 只接受 {@link TaskTypeEnum}，
 * <b>不提供 {@code resolve(String)} 重载</b> —— 让「拿字符串来查」在编译期就无处安放，
 * 字符串到枚举的转换必须在更外层用 {@link TaskTypeEnum#resolve} 显式完成并处理非法值。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Slf4j
@Component
public class TaskProgressStrategyFactory {

    private final List<TaskProgressStrategy> strategies;

    public TaskProgressStrategyFactory(List<TaskProgressStrategy> strategies) {
        this.strategies = strategies;
        // 启动期自检：重复注册会让后注册的那个永远拿不到事件，且完全无声
        long distinct = strategies.stream().map(TaskProgressStrategy::supportType).distinct().count();
        if (distinct != strategies.size()) {
            throw new IllegalStateException("TaskProgressStrategy 存在重复的 supportType，实现数="
                    + strategies.size() + "，去重后=" + distinct);
        }
        log.info("[任务策略工厂] 已装配 {} 个进度策略: {}", strategies.size(),
                strategies.stream().map(s -> s.supportType().getValue()).toList());
    }

    /**
     * @return 对应策略；无实现时返回 null（由调用方落丢弃流水，不抛异常打断整批事件处理）
     */
    public TaskProgressStrategy resolve(TaskTypeEnum taskType) {
        if (taskType == null) {
            return null;
        }
        return strategies.stream()
                .filter(s -> s.supportType() == taskType)
                .findFirst()
                .orElse(null);
    }
}
