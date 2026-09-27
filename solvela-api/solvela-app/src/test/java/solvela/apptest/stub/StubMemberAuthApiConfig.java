package solvela.apptest.stub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import solvela.enums.GenderEnum;
import solvela.member.api.EmailCodeFailReason;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.EmailBindFailReason;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.MemberContactView;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.member.api.PhoneBindFailReason;
import solvela.member.api.SmsCodeFailReason;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可数调用次数的 {@link MemberAuthApi} 桩，供需要"验证回源发生了几次"的测试用。
 *
 * <h3>🔴 为什么这个类不能待在 {@code solvela.app} 包下</h3>
 * {@code AppApplication} 的 {@code @ComponentScan({"solvela.app", "solvela.auth"})}
 * 是<b>显式列出的第二个 {@code @ComponentScan}</b>（与 {@code @SpringBootApplication}
 * 自带的那个并存）。Boot 的 {@code TestTypeExcludeFilter} 只保证「当前测试类自己的
 * {@code @TestConfiguration}」不会被它自身触发的扫描重复拾取——它<b>不会</b>排除
 * <i>其它</i>测试类、恰好也放在被扫描包里的 {@code @TestConfiguration}。
 *
 * <p>{@code ApiContractTest} 原来就是这么栽的：它的桩曾经是个内部类，长在
 * {@code solvela.app.web} 下（被扫描的 {@code solvela.app.**} 范围内）。
 * 本类刚加进来、且恰好也需要一个 {@code @Primary MemberAuthApi} 桩时，两边当场相撞——
 * 因为 {@code AppApplication} 自己的显式扫描会<b>独立于</b>任何一个测试类的 {@code @Import}，
 * 把长在被扫描包下的 {@code @TestConfiguration} 全都扫进同一个 {@code ApplicationContext}，
 * 与各自 {@code @Import} 进来的那份撞成 {@code BeanDefinitionOverrideException}；
 * 就算改个方法名躲开重名，两个 {@code @Primary} 候选人还是会撞成
 * {@code NoUniqueBeanDefinitionException}。{@code ApiContractTest} 的桩后来也搬到了
 * 本包下的 {@link ApiContractDownstreamStub}，两边才都清净。
 *
 * <p>所以这类"打算被别的测试复用"的桩，必须放在 {@code solvela.app} 与
 * {@code solvela.auth} 两个前缀<b>都覆盖不到</b>的包里——本类所在的
 * {@code solvela.apptest.*} 满足这一点（Spring 的包扫描按目录段前缀匹配，
 * {@code solvela/apptest/...} 不是 {@code solvela/app/...} 的子目录）。
 */
@TestConfiguration
public class StubMemberAuthApiConfig {

    private final AtomicInteger authIdentityCalls = new AtomicInteger();

    /** 会员不存在或状态异常时用这个 —— 与 {@link MemberAuthApi#getAuthIdentity} 的真实约定一致 */
    public boolean identityMissingFor(Long memberId) {
        return memberId != null && memberId < 0;
    }

    /** getAuthIdentity 被真正调用（即真的发生了一次"回源"）的次数 */
    public int authIdentityCallCount() {
        return authIdentityCalls.get();
    }

    public void resetCallCount() {
        authIdentityCalls.set(0);
    }

    @Bean
    @Primary
    public MemberAuthApi stubMemberAuthApi() {
        return new MemberAuthApi() {
            @Override
            public LoginChallengeCodeResult sendChallengeCode(LoginChallengeCmd cmd) {
                throw new UnsupportedOperationException("观察档凭票流程由会员域自己的用例负责");
            }

            @Override
            public MemberAuthResult verifyChallenge(LoginChallengeCmd cmd) {
                throw new UnsupportedOperationException("观察档凭票流程由会员域自己的用例负责");
            }

            @Override
            public MemberAuthResult authenticate(MemberAuthCmd cmd) {
                throw new UnsupportedOperationException("这个桩只实现了 getAuthIdentity，按需再补");
            }

            @Override
            public MemberRegisterResult register(MemberRegisterCmd cmd) {
                throw new UnsupportedOperationException("这个桩只实现了 getAuthIdentity，按需再补");
            }

            /**
             * 发码桩：契约测试只验网关那张翻译表，域的规则（限频、静默不寄）
             * 由会员域自己的用例负责。这里按邮箱前缀分派到各个 reason。
             */
            @Override
            public EmailCodeSendResult sendEmailCode(EmailCodeSendCmd cmd) {
                if (cmd.email() == null || !cmd.email().contains("@")) {
                    return EmailCodeSendResult.fail(EmailCodeFailReason.BAD_EMAIL_FORMAT);
                }
                if (cmd.email().startsWith("busy@")) {
                    return EmailCodeSendResult.tooFrequent(42L);
                }
                if (cmd.email().startsWith("quota@")) {
                    return EmailCodeSendResult.dailyLimit(3600L);
                }
                if (cmd.email().startsWith("broken@")) {
                    return EmailCodeSendResult.fail(EmailCodeFailReason.SEND_FAILED);
                }
                return EmailCodeSendResult.ok();
            }

            /**
             * 发短信码桩：与邮箱那个同一个做法，按手机号前缀分派到各个 reason，
             * 让网关那张短信翻译表每条分支都能被真实 HTTP 请求走一遍。
             */
            @Override
            public SmsCodeSendResult sendSmsCode(SmsCodeSendCmd cmd) {
                if (cmd.phone() == null || !cmd.phone().matches("[0-9]{11}")) {
                    return SmsCodeSendResult.fail(SmsCodeFailReason.BAD_PHONE_FORMAT);
                }
                if (cmd.phone().startsWith("13800")) {
                    return SmsCodeSendResult.tooFrequent(42L);
                }
                if (cmd.phone().startsWith("13900")) {
                    return SmsCodeSendResult.dailyLimit(3600L);
                }
                if (cmd.phone().startsWith("13700")) {
                    return SmsCodeSendResult.fail(SmsCodeFailReason.SEND_FAILED);
                }
                return SmsCodeSendResult.ok();
            }

            @Override
            public MemberPhoneBindResult bindPhone(MemberPhoneBindCmd cmd) {
                if (cmd.newPhone() != null && cmd.newPhone().startsWith("13911")) {
                    return MemberPhoneBindResult.fail(PhoneBindFailReason.PHONE_TAKEN);
                }
                return MemberPhoneBindResult.ok();
            }

            @Override
            public MemberContactView getContact(Long memberId) {
                return new MemberContactView("138****8000", "a***@example.com", true);
            }

            @Override
            public MemberEmailBindResult bindEmail(MemberEmailBindCmd cmd) {
                if (cmd.newEmail() != null && cmd.newEmail().startsWith("taken@")) {
                    return MemberEmailBindResult.fail(EmailBindFailReason.EMAIL_TAKEN);
                }
                return MemberEmailBindResult.ok();
            }

            @Override
            public MemberPasswordResetResult resetPassword(MemberPasswordResetCmd cmd) {
                return MemberPasswordResetResult.ok(0);
            }

            @Override
            public MemberIdentity getAuthIdentity(Long memberId) {
                authIdentityCalls.incrementAndGet();
                if (identityMissingFor(memberId)) {
                    return null;
                }
                return new MemberIdentity(memberId, "sv" + memberId, "会员" + memberId, null,
                        GenderEnum.UNKNOWN);
            }

            @Override
            public void recordLogout(MemberLogoutCmd cmd) {
            }
        };
    }
}
