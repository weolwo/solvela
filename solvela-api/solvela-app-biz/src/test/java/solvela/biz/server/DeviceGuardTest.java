package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.auth.device.DeviceTokenCodec;
import solvela.member.device.DeviceGuard;
import solvela.member.risk.feature.LoginCounters;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设备维度的失败计数与一机多号的计数，连真实 Redis。
 *
 * <p>2026-10-17：设备闸门里的判断（登录频次、一机多号、dry-run）已删 ——
 * 频次归网关限流，「超没超」归风控的 {@code DEVICE_FAIL_LIMIT} / {@code DEVICE_FANOUT}，
 * 阈值判断由 {@code LoginRuleTest} 覆盖。这里只钉计数本身：写进去的数对不对、窗口里的成员会不会整个消失。
 *
 * <p>每条用例用一个全新的 deviceId，避免互相烧配额 —— 计数键是按设备分的。
 *
 * @Date 2026-09-09
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DeviceGuardTest {

    @Autowired
    private DeviceGuard guard;

    /** 🔴 不自己拼 Redis 键，键只在 LoginCounters 出现一次 */
    @Autowired
    private LoginCounters counters;

    private static String freshDevice() {
        return DeviceTokenCodec.newDeviceId();
    }

    @Test
    @DisplayName("🔴 deviceId 为空（含全空白）→ 不计数、不抛")
    void 没有设备号不计数() {
        for (String none : new String[]{null, "", "  "}) {
            guard.recordLoginFailure(none);
        }
        assertEquals(0L, counters.read(LoginCounters.Counter.DEVICE_FAIL, "  "),
                "全空白的设备号被当成真设备计数，所有这样的请求就共用了一个计数器");
    }

    @Test
    @DisplayName("连续失败 → 计数准确累加（风控的 DEVICE_FAIL_LIMIT 读的就是这个数）")
    void 失败计数累加() {
        String device = freshDevice();

        for (int i = 0; i < 11; i++) {
            guard.recordLoginFailure(device);
        }

        assertEquals(11L, counters.read(LoginCounters.Counter.DEVICE_FAIL, device));
    }

    @Test
    @DisplayName("同一会员反复登录不算多号 —— 记的是【不同】会员数")
    void 同一会员不累加() {
        String device = freshDevice();

        for (int i = 0; i < 13; i++) {
            assertEquals(1L, counters.addToSet(LoginCounters.Counter.DEVICE_MEMBER, device, "777"),
                    "同一个会员登第 " + i + " 次就被算成多个，那是把「频次」当成了「多号」");
        }
    }

    @Test
    @DisplayName("🔴 一台持续活跃的设备，计数不会整个归零 —— 那正是养号要的效果")
    void 持续活跃不归零() throws Exception {
        String device = freshDevice();

        counters.addToSet(LoginCounters.Counter.DEVICE_MEMBER, device, "1");
        Thread.sleep(1100);

        /*
         * 成员是【各自】到点掉出去的（滑动窗口，2026-10-16），
         * 所以这条用例直接测它真正要的那件事：两个会员都还算在内。
         * 「半年前碰过的号还算在内」那一半由 SlidingWindowCounterTest 盯着。
         */
        assertEquals(2L, counters.addToSet(LoginCounters.Counter.DEVICE_MEMBER, device, "2"),
                "第二次关联之后，两个会员都该还在窗口里");
    }
}
