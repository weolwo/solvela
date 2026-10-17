package solvela.member.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.risk.feature.LoginCounters;

/**
 * 设备维度的<b>失败计数</b>：认证主干在「这次登录确实失败了」时记一笔。
 *
 * <h3>它现在只剩这一件事</h3>
 * 这个类原来是一整道「设备闸门」：登录频次、注册数、失败次数、一机多号四条规则，外加一个 dry-run 开关。
 * 那几条判断后来各有了归属，本类里对应的代码与配置（{@code solvela.member.device.guard.*}）已全部删除：
 * <ul>
 *   <li>登录频次、注册数 —— 只数「来了多少次」，归网关限流（LOGIN / REGISTER 场景的设备维度，2026-10-17）；</li>
 *   <li>失败次数「超没超」—— 风控引擎的 {@code DEVICE_FAIL_LIMIT}；</li>
 *   <li>一机多号 —— 风控引擎的 {@code DEVICE_FANOUT}；</li>
 *   <li>陪跑 / 生效 —— 每条规则在规则表里各有档位，不再是一个总开关。</li>
 * </ul>
 * 剩下的是计数的<b>写</b>那一侧：只有认证主干知道「这次是不是真的失败了」，所以由它调用本类；
 * 读那一侧在 {@code DEVICE_FAIL_LIMIT} 规则里。键只在 {@link LoginCounters} 出现一次，两侧不会对不上。
 *
 * <h3>🔴 deviceId 为空时什么都不做，而且判的是 isBlank 不是 isEmpty</h3>
 * 判成 {@code isEmpty} 的话，一个全是空白的设备号会被当成真设备去计数 ——
 * 于是<b>所有这样的请求共用同一个计数器</b>，很快集体撞上限，
 * 而日志里那个 deviceId 看起来只是「空的」。
 * 生产上走不到（{@code DeviceContract.sanitize} 只放 32 位 hex 过），
 * 但这类「兜底判空判得不够」的写法值得一次就写对。
 *
 * <p>而「为空」本身是<b>常态</b>：老客户端还没带设备令牌。
 *
 * @Date 2026-09-09
 */
@Component
@RequiredArgsConstructor
public class DeviceGuard {

    private final LoginCounters counters;

    /**
     * 记一次登录失败。密码错、账号冻结这类<b>能定位到人</b>的失败才算。
     *
     * <p>手机号格式不对不算 —— 那是客户端 bug 或用户手滑，算进去只会让阈值失真。
     */
    public void recordLoginFailure(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        counters.bump(LoginCounters.Counter.DEVICE_FAIL, deviceId);
    }
}
