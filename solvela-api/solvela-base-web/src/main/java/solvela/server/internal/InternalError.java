package solvela.server.internal;

/**
 * 服务间调用的错误体。
 *
 * <p>比 C 端的 {@code ApiErrorResponse} 简单：调用方是网关，不是人，
 * 它只需要「哪一类错、原文是什么、去哪查日志」三样东西。
 *
 * @param code    稳定的机器可读码
 * @param message 给<b>排查的人</b>看的原文。⚠️ 网关不得把它原样透传给终端用户 ——
 *                里面可能有表名、字段名、内部编码
 * @param traceId 链路 id，与调用方日志里的是同一个
 */
public record InternalError(String code, String message, String traceId) {
}
