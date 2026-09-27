package solvela.apptest.stub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import solvela.enums.GenderEnum;
import solvela.member.api.AuthFailReason;
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
import solvela.member.api.RegisterFailReason;

/**
 * 桩掉会员服务，供 {@code ApiContractTest} 用。只按<b>输入形状</b>给结果，不做任何真实逻辑 ——
 * 它存在的意义是让网关那张翻译表跑起来，不是模拟一个会员域。
 *
 * <p>本类原本是 {@code ApiContractTest} 的一个内部静态类，2026-09-03 挪到这里——不是因为
 * 想抽象复用，而是因为它<b>撞了另一个测试</b>：这里内容跟以前完全一样，只是搬了个家。
 * 原因见 {@link StubMemberAuthApiConfig} 的类注释——同一类问题，两个受害者。
 */
@TestConfiguration
public class ApiContractDownstreamStub {

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
                // 只区分「像不像手机号」这一件事：契约测试要的是
                // BAD_PHONE_FORMAT → 400、BAD_CREDENTIALS → 401 这两条映射成立
                if (cmd.identity() == null || !cmd.identity().matches("[0-9]{11}")) {
                    return MemberAuthResult.fail(AuthFailReason.BAD_PHONE_FORMAT);
                }
                return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
            }

            /**
             * 桩：按手机号的最后一位分派到各个 reason，让网关那张注册翻译表
             * 每条分支都能被真实 HTTP 请求走一遍。
             * 域自己的规则（格式、强度、限频）由会员域的测试负责，这里不重复。
             */
            @Override
            public MemberRegisterResult register(MemberRegisterCmd cmd) {
                if (cmd.identity() == null || !cmd.identity().matches("[0-9]{11}")) {
                    return MemberRegisterResult.fail(RegisterFailReason.BAD_PHONE_FORMAT);
                }
                if (cmd.identity().endsWith("1")) {
                    return MemberRegisterResult.fail(RegisterFailReason.PHONE_TAKEN);
                }
                if (cmd.identity().endsWith("2")) {
                    return MemberRegisterResult.fail(RegisterFailReason.WEAK_PASSWORD);
                }
                if (cmd.identity().endsWith("3")) {
                    return MemberRegisterResult.tooManyAttempts(90L);
                }
                return MemberRegisterResult.ok(new MemberIdentity(
                        1000000001L, "sv1000000001", "会员1000000001", null, GenderEnum.UNKNOWN));
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
                return null;
            }

            @Override
            public void recordLogout(MemberLogoutCmd cmd) {
            }
        };
    }
}
