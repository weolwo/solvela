package net.lab1024.sa.base.module.support.job.core;

import net.lab1024.sa.base.module.support.job.constant.SmartJobLaneEnum;

/**
 * 一个定时任务执行器的注册信息。
 *
 * <p>{@code handlerClassName} 存的是 {@code AopUtils.getTargetClass()} 解出的<b>真实类名</b>，
 * 只作展示与排查用 —— <b>它不参与任何匹配</b>，匹配一律走 {@link #name()}。
 * 这个区分是本次重构的要点：类名可以变，name 不能变。
 *
 * @author alaric
 * @date 2026-08-11
 */
public record SmartJobHandlerMeta(String name,
                                  String title,
                                  String group,
                                  SmartJobLaneEnum lane,
                                  boolean idempotent,
                                  int bizDateOffset,
                                  int defaultTimeoutSeconds,
                                  JobParam[] params,
                                  String handlerClassName,
                                  SmartJob instance) {
}
