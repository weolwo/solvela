package solvela.base.module.config;

/**
 * 一条系统配置被写过了（新增 / 修改）。由 {@link ConfigService} 在刷新完本进程的缓存之后发布。
 *
 * <p>它存在的原因：有的配置不只本进程在用。网关限流的两条全局参数要经 Redis 快照下发到网关，
 * 改完得立刻重发一份 —— 而 base-data 不该知道「谁在乎哪条配置」。关心的一方自己监听、自己按 key 过滤。
 *
 * <p>只在<b>经过应用写入</b>时才有：有人绕过应用直接改库，不会有这个事件（与缓存刷新同一个边界）。
 *
 * @param configKey 被写的那条配置的 key（大小写以库里为准）
 * @Date 2026-10-17
 */
public record ConfigChangedEvent(String configKey) {
}
