package solvela.marketing.api;

import java.util.Map;

/**
 * C 端发起的一次「参与」。
 *
 * <h3>🔴 这里没有 poolCode，是刻意的</h3>
 * 抽哪个奖池由<b>脚本</b>按次数、身份、时段算出来。让客户端传 poolCode，
 * 等于让它绕过整段编排自己挑一个池子抽 —— 那是运营配置里最不该开放的一个参数。
 * 收 poolCode 的是 {@link DrawApi}，那个不对外。
 *
 * @param activityCode 活动编码
 * @param memberId     会员号。由网关从登录态解析后显式传入，不靠 ThreadLocal ——
 *                     域服务不该有「当前登录用户」这个概念
 * @param requestId    幂等键，<b>由客户端生成并携带</b>（一次点击一个 id）。
 *                     网关生成的话，客户端重试会拿到不同的 id，幂等当场失效
 * @param times        抽几次。<b>客户端表达意愿</b>（用户点的是「单抽」还是「十连抽」），
 *                     由网关按 {@link DrawLimits#MAX_TIMES} 封顶后传进来 ——
 *                     它是不可信输入，不封顶的话一个 {@code times=100000} 就能打穿。
 *                     <p>🔴 <b>最终抽几次由编排脚本裁决</b>：脚本拿到这个数当参考，
 *                     结合剩余次数、身份、时段算出真正的次数再调抽奖函数。
 *                     与 poolCode 是同一套分工 —— 客户端说想要什么，脚本决定给什么
     *                     <p>⚠️ 类型是装箱的 {@code Integer} 而不是 {@code int}，因为本对象是
     *                     <b>跨进程 body</b>：原生 int 遇到 JSON 里缺这个字段会让 Jackson 直接报
     *                     「Cannot map null into type int」-> 400。滚动发布时老调用方不带 times，
     *                     整条抽奖链路会当场 400。规范构造器把 null 归一成 1（不传 = 单抽），
     *                     所以访问器<b>永不返回 null</b>
 * @param params       玩法自定义参数，可为 null（域内会换成空 Map）。
 *                     这是「所有活动共用一组 api」必付的代价 ——
 *                     但<b>别让脚本自己判空</b>：必填项在 {@code ScriptScene} 里声明，
 *                     执行前校验，报的是变量名而不是脚本里的 NPE
 */
public record ActivityDrawCmd(String activityCode, Long memberId, String requestId, Integer times,
                              Map<String, Object> params) {

    public ActivityDrawCmd {
        // 不传等于单抽。归一放在这里而不是各调用方：少归一一处，那一处就会拿到 null
        times = times == null ? 1 : times;
    }
}
