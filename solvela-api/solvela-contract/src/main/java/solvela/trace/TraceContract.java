package solvela.trace;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 链路 id 的<b>线上约定</b>：头的名字、什么样的值算合法、不合法时怎么生成一个。
 *
 * <h3>为什么这三样必须共享，而过滤器实现可以各写各的</h3>
 * 链路 id 是<b>两个进程之间的协议</b>：网关把它写进请求头，biz 把它读回 MDC。
 * 「读进来怎么处理」是本地行为，各端自己实现没问题；但<b>头名和合法性规则一旦两边不一致，
 * 链路就断了，而且断得极其隐蔽</b> —— 网关认的 id 被下游判非法、下游于是自己生成一个新的，
 * 两边日志都有 traceId、都很正常，只是对不上。没有任何报错。
 *
 * <p>所以这三样住在 {@code solvela-contract}：这个模块零第三方运行时依赖、
 * 全仓每个模块（包括不依赖任何 base 的网关）都有它。
 *
 * <p>⚠️ 改这里等于改线上协议，两个进程必须同时发版。
 *
 * <h3>🔴 客户端传来的 id 必须校验</h3>
 * 允许客户端指定 traceId 是有价值的（App 侧能把自己的埋点和服务端日志串起来），
 * 但直接采信就是一个<b>日志注入</b>口子：值里塞一段编码过的换行加一行伪造日志，
 * 日志文件里就会出现一条看起来完全正常、实际是别人写的记录。采集到 ELK 之后，
 * 谁也分不出哪条是真的。
 *
 * <p>所以只接受「32 位以内的字母数字和连字符」。不合规就当没传、自己生成一个 ——
 * 不报错，因为这不是用户该关心的事。
 */
public final class TraceContract {

    /**
     * MDC 的 key，同时也是请求/响应头的名字。
     *
     * <p>业务代码取值不要 {@code MDC.get(TraceContract.KEY)}，走各端自己的 {@code Trace.id()} ——
     * 这个字符串一旦散落在几个文件里，改名就会漏掉一处，而漏掉的表现只是
     * 「这个字段莫名其妙变空了」，不会有任何报错。
     */
    public static final String KEY = "traceId";

    private static final int MAX_LENGTH = 32;

    private TraceContract() {
    }

    /**
     * 把客户端传来的候选值收敛成一个合法的链路 id：合规就原样用，不合规就生成一个新的。
     *
     * <p>返回值<b>永远不为 null</b> —— 调用方不需要判空，也就不会有人忘了判。
     */
    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return generate();
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-';
            if (!allowed) {
                return generate();
            }
        }
        return candidate;
    }

    /** 生成一个新的链路 id。刻意不用 UUID：16 位 hex 已经够区分并发请求，而且日志里短一半 */
    public static String generate() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
