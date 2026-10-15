package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 异常登录的<b>确认</b>：「你的账号刚在 XX 登录，是你吗？」（方案 §8.1）。
 *
 * <h3>为什么它不挂在 {@link MemberStepUpApi} 上</h3>
 * 试过，编译不过 —— 而那次编译失败说出了一件真事：
 * {@code MemberStepUpService} 实现 step-up 契约，而这两个方法的实现要<b>反过来</b>
 * 依赖它（判「当前这台设备受不受信任」）。同一个契约上就是循环。
 *
 * <p>所以两者是两件事：step-up 回答「这台设备此刻够不够可信」，
 * 这里回答「那一次登录到底是不是本人」—— 后者<b>用前者的答案</b>，而不是它的一部分。
 *
 * <h3>它存在的理由：风控最缺的是答案</h3>
 * 规则给出的是猜测。没有标注，「打扰了多少本人、漏过了多少盗号」只能靠猜，
 * 阈值也就只能靠感觉调。一个两颗按钮的弹窗，是这套系统能拿到的最便宜、最可信的标注来源。
 *
 * <p>路径前缀 {@code /internal} 同 {@link DeviceApi}：入口层必须把它整体挡在外面。
 *
 * @Date 2026-10-16
 */
@HttpExchange("/internal/member/security")
public interface MemberSecurityApi {

    /**
     * 待确认的提问。
     *
     * <h3>🔴 只在【受信任的老设备】上、且不问当前这一台</h3>
     * 盗号者就在那台新设备上 —— 在那里问，他会点「是我」，而那条「确认本人」会系统性地
     * 污染误伤率，让我们以为规则比实际更准，然后把阈值调松。
     * 两个条件都由域判（{@code cmd.deviceId()} 是入口层从设备令牌解析的，客户端改不了）。
     *
     * <p>不满足时返回<b>空列表</b>，不是报错 —— 客户端冷启动时都会调它一次，
     * 而「这台设备不受信任」是最常见的情况，不是错误。
     */
    @PostExchange("/prompts")
    List<SecurityPromptView> list(@RequestBody StepUpCmd cmd);

    /**
     * 回答一条。{@code isMe = false} 等同「这不是我」的全套处置 ——
     * 域里<b>走同一个方法</b>，不另写一遍：两个入口的后果一旦漂移，
     * 用户从哪儿点的就决定了他被保护到什么程度。
     *
     * @return 这一答有没有被受理。单号不存在、已答过、或者不在受信任设备上都是 false
     */
    @PostExchange("/prompts/answer")
    boolean answer(@RequestBody SecurityPromptAnswerCmd cmd);
}
