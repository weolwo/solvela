package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberVerifyChannelEnum;
import solvela.member.Member;
import solvela.member.api.MemberLoginType;
import solvela.member.auth.MemberAuthDao;
import solvela.member.sms.MemberSmsCodeService;

/**
 * 核身路由：<b>决策只说强度，通道由这里选</b>（方案 §3.7）。
 *
 * <h3>为什么这件事不能写在决策里</h3>
 * 规则看不到、也不该看到「这个账号绑了什么」。让规则直接给出「发短信」的话，
 * 每条要 OTP 的规则都得自己判一遍「手机号绑了没有、短信接通了没有」—— 二十条规则二十份判断，
 * 而它们漂移的第一天不会有任何报错。
 *
 * <p>所以规则只说强度（{@code OTP} / {@code OTP_STRONG}），这里回答「那就发到哪」。
 *
 * <h3>换通道的意义</h3>
 * {@code OTP_STRONG} 要求<b>避开登录时用的那个因子</b>：密码复用的人，本站密码和邮箱密码
 * 往往在同一次泄露里一起出去 —— 那时往同一个邮箱发码，证明不了任何事。
 *
 * <p>🔴 强通道不可用时<b>退回</b>普通 OTP，而不是拒绝登录。拒绝的代价落在主人身上
 * （他除了等什么也做不了），而退回之后攻击者仍然要有那个邮箱才进得去。
 * 退回时该给主人发一封「我们拦下了一次可疑登录」——
 * 那是 {@link RiskAction#NOTIFY_OWNER}，还没接（方案 §9 的 P1）。
 *
 * <h3>一条都没有的时候</h3>
 * 返回 {@code null}，由认证主干翻成 {@code LOGIN_VERIFY_UNAVAILABLE}，<b>在签票之前</b>。
 * 今天唯一会走到的组合是：手机号账号 + 短信还没接 + 没绑邮箱。
 *
 * <h3>🔴 联系方式单独查一次，不搭登录那个查询的车</h3>
 * {@code selectForLogin} 刻意只取认证需要的列（见 {@link MemberAuthDao} 的类注释），
 * 手机号和邮箱都不在其中。为了这条很少走到的分支把它们加进去，等于让所有人的
 * 手机号、邮箱<b>每次登录</b>都进一遍结果集 —— 这里宁可多一次点查。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyRouter {

    private final MemberAuthDao memberAuthDao;

    private final MemberSmsCodeService smsCodeService;

    /** 新绑的联系方式在冷静期内一律当「这条通道不可用」（方案 §6.4） */
    private final solvela.member.contact.ContactCoolingStore coolingStore;

    /**
     * 选定的通道。
     *
     * @param channel  码发到哪
     * @param fellBack 本来要换通道，但那条不可用，退回到了登录时用的那条。
     *                 <b>只用来记日志和将来通知主人</b>，不改变给客户端的回答 ——
     *                 「我们本想换个通道但没换成」对用户没有任何意义，对攻击者却是一条调参反馈
     */
    public record Route(MemberVerifyChannelEnum channel, boolean fellBack) {
    }

    /**
     * 这一次核身走哪条通道。<b>一条都没有时返回 null。</b>
     *
     * @param decision 后置结论，只区分 {@code OTP_STRONG} 与其余（§3.7 的表只有这两列）
     */
    public Route route(MemberLoginType loginType, Long memberId, MemberRiskDecisionEnum decision) {
        if (memberId == null) {
            return null;
        }
        Member contacts = memberAuthDao.selectContacts(memberId);
        if (contacts == null) {
            return null;
        }
        /*
         * 🔴 冷静期内的通道<b>当作没有</b>，而不是单独报一个错。
         * 这样「还有另一条通道就走另一条、一条都没有才拒绝」这套逻辑一行都不用改 ——
         * 而那正是对的行为：主人新绑的邮箱还在冷静期，但他的手机号还能收码。
         */
        boolean emailReady = !SolvelaStringUtil.isBlank(contacts.getEmail())
                && !coolingStore.cooling(memberId, MemberVerifyChannelEnum.EMAIL);
        boolean smsReady = !SolvelaStringUtil.isBlank(contacts.getPhone())
                && !coolingStore.cooling(memberId, MemberVerifyChannelEnum.SMS)
                && smsCodeService.deliverable();

        MemberVerifyChannelEnum sameAsLogin = loginType == MemberLoginType.PHONE_PASSWORD
                ? MemberVerifyChannelEnum.SMS
                : MemberVerifyChannelEnum.EMAIL;
        MemberVerifyChannelEnum other = sameAsLogin == MemberVerifyChannelEnum.SMS
                ? MemberVerifyChannelEnum.EMAIL
                : MemberVerifyChannelEnum.SMS;

        if (decision == MemberRiskDecisionEnum.OTP_STRONG) {
            if (ready(other, emailReady, smsReady)) {
                return new Route(other, false);
            }
            if (ready(sameAsLogin, emailReady, smsReady)) {
                // 🔴 退回，不是拒绝。日志留一行：切生效之后要能数出「有多少次本该换通道却没换成」
                log.info("【核身路由】要求换通道但 {} 不可用，退回 {}", other, sameAsLogin);
                return new Route(sameAsLogin, true);
            }
            return null;
        }

        if (ready(sameAsLogin, emailReady, smsReady)) {
            return new Route(sameAsLogin, false);
        }
        /*
         * 登录时用的那条也不可用：手机号账号 + 短信没接。这时邮箱算不上「换通道」——
         * 它是唯一一条，用它比把主人关在门外好。
         */
        if (ready(other, emailReady, smsReady)) {
            return new Route(other, false);
        }
        return null;
    }

    private static boolean ready(MemberVerifyChannelEnum channel, boolean emailReady, boolean smsReady) {
        return channel == MemberVerifyChannelEnum.EMAIL ? emailReady : smsReady;
    }
}
