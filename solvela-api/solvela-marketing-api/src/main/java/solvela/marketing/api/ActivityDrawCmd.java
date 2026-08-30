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
 * @param params       玩法自定义参数，可为 null（域内会换成空 Map）。
 *                     这是「所有活动共用一组 api」必付的代价 ——
 *                     但<b>别让脚本自己判空</b>：必填项在 {@code ScriptScene} 里声明，
 *                     执行前校验，报的是变量名而不是脚本里的 NPE
 */
public record ActivityDrawCmd(String activityCode, Long memberId, String requestId,
                              Map<String, Object> params) {
}
