package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskLabelEnum;
import solvela.enums.MemberRiskLabelSourceEnum;
import solvela.member.api.SecurityPromptAnswerCmd;
import solvela.member.api.SecurityPromptView;
import solvela.member.api.SessionReportCmd;
import solvela.member.api.StepUpCmd;
import solvela.member.stepup.MemberStepUpService;

import java.util.ArrayList;
import java.util.List;

/**
 * 「你的账号刚在 XX 登录，是你吗？」—— 在<b>主人的老设备</b>上问（方案 §8.1）。
 *
 * <h3>为什么要问</h3>
 * 新设备登录提醒（邮件 + 站内信）只是<b>告知</b>，答不回来。而风控最缺的恰恰是答案：
 * 没有标注，「打扰了多少本人、漏过了多少盗号」只能靠猜。一个两颗按钮的弹窗，
 * 是这套系统能拿到的最便宜、最可信的标注来源。
 *
 * <h3>🔴 绝不在新设备本身上问</h3>
 * 盗号者就在那台设备上 —— 他会点「是我」，而那条「确认本人」会系统性地污染误伤率，
 * 让我们以为规则比实际更准，然后把阈值调松。所以列表要同时满足两件事：
 * <ul>
 *   <li>当前这台设备<b>受信任</b>（老设备）；</li>
 *   <li>问的不是当前这一台。</li>
 * </ul>
 * 两个条件缺一不可：只滤设备号的话，盗号者在<b>第二台</b>新设备上就能替主人回答。
 *
 * <h3>「不是我」等同「这不是我」</h3>
 * 同一套处置：冻结资产出口、撤销其余设备信任、把那次登录标成确认被盗。
 * 两个入口的后果必须完全一样 —— 不一样的话，用户从哪儿点的决定了他被保护到什么程度。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityPromptService implements solvela.member.api.MemberSecurityApi {

    private final SecurityPromptStore store;

    private final MemberStepUpService stepUpService;

    private final RiskLabelService riskLabelService;

    /**
     * 待确认列表。<b>不满足「在受信任的老设备上」时一律返回空</b>。
     */
    @Override
    public List<SecurityPromptView> list(StepUpCmd cmd) {
        if (cmd.memberId() == null || SolvelaStringUtil.isBlank(cmd.deviceId())) {
            return List.of();
        }
        if (!stepUpService.check(cmd).trusted()) {
            // 🔴 在一台不受信任的设备上不问 —— 盗号者的那台正是不受信任的
            return List.of();
        }
        List<SecurityPromptView> views = new ArrayList<>();
        for (SecurityPromptStore.Prompt prompt : store.list(cmd.memberId())) {
            if (prompt.deviceId().equals(cmd.deviceId())) {
                // 就是这一台：绝不在新设备本身上问
                continue;
            }
            views.add(new SecurityPromptView(prompt.promptId(), prompt.deviceType(),
                    prompt.location(), prompt.loginEpochSecond() * 1000L));
        }
        return List.copyOf(views);
    }

    /**
     * 回答一条。
     *
     * @return 这一答有没有被受理（单号不存在、已答过、不在受信任设备上都是 false）
     */
    @Override
    public boolean answer(SecurityPromptAnswerCmd cmd) {
        if (cmd.memberId() == null || SolvelaStringUtil.isBlank(cmd.currentDeviceId())) {
            return false;
        }
        StepUpCmd trustCmd = StepUpCmd.of(cmd.memberId(), cmd.currentDeviceId(), cmd.clientIp());
        if (!stepUpService.check(trustCmd).trusted()) {
            /*
             * 🔴 答题也要在受信任的设备上。只在列表那一步拦的话，
             * 盗号者拿到一个单号（比如从主人的截图里）就能直接调这个接口点「是我」。
             */
            log.info("【安全确认】不受信任的设备来答题，忽略, memberId: {}", cmd.memberId());
            return false;
        }
        SecurityPromptStore.Prompt prompt = store.take(cmd.memberId(), cmd.promptId());
        if (prompt == null) {
            return false;
        }
        if (prompt.deviceId().equals(cmd.currentDeviceId())) {
            // 走不到（列表里就滤掉了），但接口是公开的，兜一道
            log.warn("【安全确认】有人试图在被问的那台设备上自答, memberId: {}", cmd.memberId());
            return false;
        }

        if (cmd.isMe()) {
            riskLabelService.labelLogin(cmd.memberId(), prompt.deviceId(), prompt.loginEpochSecond(),
                    MemberRiskLabelEnum.OWNER, MemberRiskLabelSourceEnum.OLD_DEVICE_CONFIRM);
            log.info("【安全确认】主人确认是本人, memberId: {}, 设备: {}", cmd.memberId(), prompt.deviceId());
            return true;
        }

        /*
         * 「不是我」= 「这不是我」。🔴 走<b>同一个</b>方法，不在这里重写一遍三件事 ——
         * 两个入口的后果一旦漂移，用户从哪儿点的就决定了他被保护到什么程度。
         */
        stepUpService.reportNotMe(new SessionReportCmd(cmd.memberId(), prompt.deviceId(),
                prompt.loginEpochSecond(), cmd.currentDeviceId(), cmd.clientIp()));
        return true;
    }

    /**
     * 新设备登录成功、风控放行时记一条。由 {@code NewDeviceLoginNotifier} 调用 ——
     * 「这台设备新不新」的判断只该有一处。
     */
    public void onNewDeviceLogin(Long memberId, String deviceId, String deviceType,
                                 String location, long loginEpochSecond) {
        store.add(memberId, deviceId, deviceType, location, loginEpochSecond);
    }
}
