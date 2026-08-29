package solvela.code;

/**
 * 一个业务错误的<b>身份</b>。
 *
 * <h3>为什么没有数字码了</h3>
 * 这个接口原先有 {@code getCode()}（10001 / 30001 / 50002…）和 {@code getLevel()}，
 * 两者都只为一件事存在：填进 {@code ResponseDTO} 的 {@code code} / {@code level} 字段。
 * 那个信封去掉之后，错误的类别由 <b>HTTP 状态码</b>表达、身份由<b>枚举常量名</b>表达，
 * 数字码就没有任何读者了 —— 连带那套「按号段注册、启动时校验有没有重叠」的机制
 * （{@code ErrorCodeRegister} / {@code ErrorCodeRangeContainer}）也一起删了：
 * 它唯一的作用是验证一个没人读的数字不重复。
 *
 * <p>留下两个数字身份（{@code 30001} 和 {@code PARAM_ERROR}）指同一件事，
 * 结果一定是有人在一处用码、另一处用名，然后两边对不上。
 *
 * <h3>三者各司其职</h3>
 * <ul>
 *   <li><b>HTTP 状态码</b>给基础设施看 —— 网关熔断、APM 成功率、客户端重试策略；</li>
 *   <li><b>{@link #name()}</b>（就是枚举常量名）给客户端代码看 —— 按它分支；</li>
 *   <li><b>{@link #getMsg()}</b>给人看 —— 可直接展示。</li>
 * </ul>
 * 状态码怎么映射见 solvela-web 的 {@code ErrorStatus}：那张表住在 HTTP 层，
 * 因为共享层不该认识 {@code HttpStatus}。
 */
public interface ErrorCode {

    /**
     * 稳定的机器可读码，实现枚举天然满足它（返回常量名）。
     *
     * <p><b>改名等于改接口契约</b> —— 前端是按这个字符串分支的。
     */
    String name();

    /**
     * 给人看的一句话，可直接展示给用户。
     *
     * <p>所以不要往里放 SQL、类名、堆栈或内部 id。
     */
    String getMsg();
}
