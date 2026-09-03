package solvela.apptest.stub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import solvela.enums.GenderEnum;
import solvela.member.api.MemberAuthApi;
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
 * {@code AppApplication} 的 {@code @ComponentScan({"solvela.app", "solvela.member.session"})}
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
 * {@code solvela.member.session} 两个前缀<b>都覆盖不到</b>的包里——本类所在的
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
            public MemberAuthResult authenticate(MemberAuthCmd cmd) {
                throw new UnsupportedOperationException("这个桩只实现了 getAuthIdentity，按需再补");
            }

            @Override
            public MemberRegisterResult register(MemberRegisterCmd cmd) {
                throw new UnsupportedOperationException("这个桩只实现了 getAuthIdentity，按需再补");
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
