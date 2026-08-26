package solvela.base.module.jobspi.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.BooleanSupplier;

/**
 * 定时任务执行上下文。
 *
 * <p>取代原来那个裸的 {@code String param}。三个字段是这次重构的重点：
 *
 * <p>🔴 <b>{@link #dbNow} / {@link #triggerTime} 都来自数据库时钟</b>（铁律 9/10）。
 * 它们存在的意义就是消灭执行器里的 {@code LocalDateTime.now()} ——
 * 定时任务是这个坑的重灾区，每个执行器都天然想写 {@code LocalDate.now()}。
 * 同一条铁律在 {@code TaskPeriodResolver} 里已经立过一次，
 * 交接文档记着 497 行数据的实测代价。
 *
 * <p>🔴 <b>{@link #bizDate} 是数据类任务的运营刚需。</b>
 * 「昨天的统计跑错了，重跑 8 月 9 号那天」—— 没有这个字段根本表达不了，
 * 只能改系统时间或手改数据。
 * 它的基准是 {@link #triggerTime} 而不是 {@link #dbNow}：
 * misfire 补跑时前者是原定触发时刻、后者是现在，
 * 用 dbNow 会让<b>所有补跑处理错日期</b>，而补跑恰恰是 bizDate 存在的意义。
 *
 * <p>🔴 <b>{@link #cancelled} 是超时能否生效的关键。</b>
 * 超时靠 {@code Future#cancel(true)}，本质是 {@link Thread#interrupt()} ——
 * <b>不响应中断的任务砍不掉</b>。循环处理批量数据的执行器，每批开头必须调
 * {@link #checkCancelled()}。
 *
 * @param shardIndex 分片序号。本期恒为 0，接口先留位
 * @param shardTotal 分片总数。本期恒为 1
 * @author alaric
 * @date 2026-08-11
 */
public record SolvelaJobContext(String jobCode,
                                String jobName,
                                String handlerName,
                                String param,
                                LocalDate bizDate,
                                LocalDateTime triggerTime,
                                LocalDateTime dbNow,
                                String traceId,
                                Long logId,
                                int retrySeq,
                                int shardIndex,
                                int shardTotal,
                                BooleanSupplier cancelled) {

    /**
     * 本次是否为重试。执行器可以据此调整行为（比如重试时跳过已成功的部分）
     */
    public boolean isRetry() {
        return retrySeq > 0;
    }

    // ==================== 参数读取 ====================

    /**
     * 按 {@code @JobParam} 声明的 key 取参数。
     *
     * <p>{@link #param()} 里存的是 JSON 对象（由后台按参数 schema 渲染的表单生成）。
     * 解析失败一律返回默认值而<b>不抛异常</b> —— 一个填错的参数不该让任务彻底跑不起来，
     * 而应该以「用了默认值」的形式继续，并由执行器决定要不要在结果摘要里说明。
     *
     * <p>⚠️ 兼容裸字符串参数：老任务的 param 可能不是 JSON（例如直接写 "30"）。
     * 那种情况下按 key 取不到，返回默认值。
     */
    public String stringParam(String key, String defaultValue) {
        Object value = this.paramMap().get(key);
        return null == value ? defaultValue : String.valueOf(value);
    }

    public int intParam(String key, int defaultValue) {
        String value = this.stringParam(key, null);
        if (null == value || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean boolParam(String key, boolean defaultValue) {
        String value = this.stringParam(key, null);
        return null == value || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /**
     * 每次调用都重新解析。参数个数是个位数、执行器也只在开头读几次，
     * 为此加缓存字段会让这个 record 不再是纯数据载体，不划算
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> paramMap() {
        if (null == param || param.isBlank()) {
            return java.util.Map.of();
        }
        try {
            java.util.Map<String, Object> map = solvela.base.json.JsonUtils
                    .parseObject(param, java.util.Map.class);
            return null == map ? java.util.Map.of() : map;
        } catch (Exception e) {
            // 不是 JSON（老任务的裸字符串参数）—— 不是错误，按空处理
            return java.util.Map.of();
        }
    }

    public boolean isCancelled() {
        return cancelled.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    /**
     * 批处理循环里每批开头调一次。被中断时抛出，由框架落成 TIMEOUT 状态。
     *
     * <p>不调它的后果不是「超时不生效」这么简单：任务会一直占着车道，
     * 而框架已经认定它超时了 —— 于是既砍不掉、又不再计入并发，是最坏的中间态。
     */
    public void checkCancelled() {
        if (this.isCancelled()) {
            throw new SolvelaJobCancelledException("任务被中断（超时或节点停机）");
        }
    }
}
