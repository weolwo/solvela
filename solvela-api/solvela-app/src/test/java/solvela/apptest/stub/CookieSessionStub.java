package solvela.apptest.stub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import solvela.auth.device.DeviceTokenCodec;
import solvela.enums.GenderEnum;
import solvela.member.api.DeviceApi;
import solvela.member.api.DeviceRegisterCmd;
import solvela.member.api.DeviceRegisterResult;
import solvela.member.api.DeviceTrust;
import solvela.member.api.EmailCodeSendCmd;
import solvela.member.api.EmailCodeSendResult;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.LoginChallengeCodeResult;
import solvela.member.api.LoginChallengeCmd;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberContactView;
import solvela.member.api.MemberEmailBindCmd;
import solvela.member.api.MemberEmailBindResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.api.MemberPasswordResetCmd;
import solvela.member.api.MemberPasswordResetResult;
import solvela.member.api.MemberPhoneBindCmd;
import solvela.member.api.MemberPhoneBindResult;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.SmsCodeSendCmd;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Web 端 cookie 会话测试（{@code CookieSessionTest}）专用的下游桩。
 *
 * <p>与 {@link ApiContractDownstreamStub} 分开：那个桩的 {@code getAuthIdentity} 恒返回 null
 * （它只验错误翻译表），而这里要「注册之后凭 cookie 真的认得出人」。
 * 放在 {@code solvela.apptest} 下的理由见 {@link StubMemberAuthApiConfig} 类注释。
 *
 * <p>注册一律成功、会员号固定为 {@link #MEMBER_ID}；令牌由网关真实签发进 Redis，测试结束由用例清掉。
 */
@TestConfiguration
public class CookieSessionStub {

    /** 测试专用会员号。真实数据不会用到这一段 */
    public static final long MEMBER_ID = 9_000_000_123L;

    /** 设备注册被真正调用（即真的新建了一台设备）的次数 */
    public final AtomicInteger deviceRegisterCalls = new AtomicInteger();

    private static MemberIdentity identity() {
        return new MemberIdentity(MEMBER_ID, "sv" + MEMBER_ID, "会员" + MEMBER_ID, null, GenderEnum.UNKNOWN);
    }

    @Bean
    @Primary
    public MemberAuthApi cookieStubMemberAuthApi() {
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
                return MemberAuthResult.ok(identity());
            }

            @Override
            public MemberRegisterResult register(MemberRegisterCmd cmd) {
                return MemberRegisterResult.ok(identity());
            }

            @Override
            public MemberIdentity getAuthIdentity(Long memberId) {
                return memberId != null && memberId == MEMBER_ID ? identity() : null;
            }

            @Override
            public void recordLogout(MemberLogoutCmd cmd) {
            }

            @Override
            public EmailCodeSendResult sendEmailCode(EmailCodeSendCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SmsCodeSendResult sendSmsCode(SmsCodeSendCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemberPhoneBindResult bindPhone(MemberPhoneBindCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemberContactView getContact(Long memberId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemberEmailBindResult bindEmail(MemberEmailBindCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemberPasswordResetResult resetPassword(MemberPasswordResetCmd cmd) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** 设备注册：用网关自己的 codec 签一个真令牌（这样 DeviceFilter 验得过），并计数 */
    @Bean
    @Primary
    public DeviceApi cookieStubDeviceApi(DeviceTokenCodec codec) {
        return (DeviceRegisterCmd cmd) -> {
            deviceRegisterCalls.incrementAndGet();
            String deviceId = DeviceTokenCodec.newDeviceId();
            return DeviceRegisterResult.ok(codec.issue(deviceId, cmd.deviceType()), deviceId);
        };
    }

    /** 下线其他设备会顺带撤销设备信任；这里只需要它不报错 */
    @Bean
    @Primary
    public MemberStepUpApi cookieStubStepUpApi() {
        return new MemberStepUpApi() {
            @Override
            public DeviceTrust check(StepUpCmd cmd) {
                return DeviceTrust.VERIFIED;
            }

            @Override
            public StepUpResult sendCode(StepUpCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StepUpResult verify(StepUpCmd cmd) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean revokeOthers(StepUpCmd cmd) {
                return true;
            }
        };
    }
}
