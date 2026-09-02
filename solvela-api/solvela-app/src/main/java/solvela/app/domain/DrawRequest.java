package solvela.app.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import solvela.marketing.api.DrawLimits;

import java.util.Map;

/**
 * 抽一次（或连抽）的入参。
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
 *                  「可能白扣一次机会」之间选一个。
 *                  <p>连抽整批共用这一个 id：重投拿回的是<b>第一次的完整结果</b>，
 *                  每一次抽奖自己的业务单号由引擎派生
 * @param times     抽几次，不传等于 1（老客户端因此原样是单抽）。
 *                  <p>🔴 <b>这是不可信输入，所以必须封顶</b>：不封的话一个
 *                  {@code times=100000} 会在一个事务里打十万轮 Redis 预扣、
 *                  十万行流水 insert、十万个派发事件。上限见 {@link DrawLimits#MAX_TIMES}。
 *                  <p>它只是<b>意愿</b> —— 用户点的是「单抽」还是「十连抽」。
 *                  真正抽几次由编排脚本结合剩余次数裁决，与 poolCode 是同一套分工
 * @param params    玩法自定义参数，可为空。必填项由脚本场景契约声明并在服务端校验
 */
public record DrawRequest(
        @NotBlank(message = "缺少请求标识") String requestId,
        @Min(value = 1, message = "抽奖次数至少为 1")
        @Max(value = DrawLimits.MAX_TIMES, message = "一次最多抽 " + DrawLimits.MAX_TIMES + " 次")
        Integer times,
        Map<String, Object> params) {

    /** 不传 times 等于单抽。校验注解对 null 不生效，所以归一放在这里 */
    public int timesOrOne() {
        return times == null ? 1 : times;
    }
}
