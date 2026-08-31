package solvela.activity.domain.dto;

/**
 * 活动下游引用的一条明细，如「奖池 3 个」。
 *
 * <p>刻意没有 {@code @Schema}：它是 {@code ActivityRefProvider} 这个 SPI 的返回类型，
 * 由 solvela-marketing 的各玩法实现、由 activity 域消费，<b>admin 从不把它作为响应体返回</b>。
 * 挂接口文档注解既不会出现在任何文档里，又让一个跨模块的能力契约看起来依赖 HTTP 层。
 *
 * @Author weolwo
 * @Date 2026-07-29
 * @param bizName 业务名称，可直接展示，如「奖池」「奖品」
 * @param count   引用数量
 */
public record ActivityRefItem(

        /** 业务名称，可直接展示，如「奖池」「奖品」 */
        String bizName,

        /** 引用数量 */
        long count) {
}
