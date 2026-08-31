package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 抽一次的入参。
 *
 * <h3>这里没有 poolCode，也没有 memberId</h3>
 * <ul>
 *   <li><b>poolCode</b>：抽哪个奖池由活动的编排脚本算。让客户端传，等于让它绕过编排自己挑池子；</li>
 *   <li><b>memberId</b>：从登录态解析，客户端传的一律不认 —— 否则就是「替别人抽奖」。</li>
 * </ul>
 *
 * @param requestId 幂等键，<b>客户端生成</b>：一次点击一个 id。
 *                  服务端生成的话，客户端重试会拿到不同的 id，幂等当场失效。
 *                  网络超时不代表没抽 —— 没有它就只能在「可能重复发奖」和
 *                  「可能白扣一次机会」之间选一个
 * @param params    玩法自定义参数，可为空。必填项由脚本场景契约声明并在服务端校验
 */
public record DrawRequest(
        @NotBlank(message = "缺少请求标识") String requestId,
        Map<String, Object> params) {
}
