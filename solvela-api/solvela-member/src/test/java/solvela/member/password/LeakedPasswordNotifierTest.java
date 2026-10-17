package solvela.member.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import solvela.base.module.redis.RedisService;
import solvela.notification.service.NotificationService;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「你正在用的密码已泄露」那条站内信。
 *
 * <h3>这里钉的三件事，全都是「它不该干的事」</h3>
 * <ol>
 *   <li><b>密码没泄露时一条都不发</b> —— 否则这条提醒立刻变成噪音；</li>
 *   <li><b>节流</b> —— 每次登录都说一遍，用户学到的是忽略 SYSTEM 分类的所有消息；</li>
 *   <li><b>任何异常都不能冒到登录上</b> —— 它挂在登录主干里，而提醒的价值高不过登录本身。</li>
 * </ol>
 *
 * @Date 2026-10-17
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("已泄露密码提醒")
class LeakedPasswordNotifierTest {

    private static final long MEMBER_ID = 900001L;

    /** 用真的表：这个类的全部意义就是「表说泄露了就提醒」，mock 掉它等于什么都没测 */
    private final WeakPasswordChecker checker = new WeakPasswordChecker();

    @Mock
    private NotificationService notificationService;

    @Mock
    private RedisService redisService;

    private LeakedPasswordNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new LeakedPasswordNotifier(checker, notificationService, redisService);
        ReflectionTestUtils.setField(notifier, "enabled", true);
        ReflectionTestUtils.setField(notifier, "throttle", Duration.ofDays(30));
        when(redisService.generateRedisKey(anyString(), anyString())).thenReturn("k");
        // 默认：首次（INCR 返回 1）
        when(redisService.increment(anyString(), anyLong())).thenReturn(1L);
    }

    @Test
    @DisplayName("密码在泄露表里 → 发一条站内信")
    void 命中就提醒() {
        notifier.onPasswordVerified(MEMBER_ID, "Password1");

        verify(notificationService).send(any());
    }

    @Test
    @DisplayName("🔴 密码没泄露 → 一条都不发，而且连节流键都不碰")
    void 没命中不提醒() {
        notifier.onPasswordVerified(MEMBER_ID, "SvRegister2026");

        verify(notificationService, never()).send(any());
        // 不该为「绝大多数正常登录」白写一次 Redis
        verify(redisService, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("🔴 节流：同一个会员第二次登录不再提醒")
    void 节流() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(2L);

        notifier.onPasswordVerified(MEMBER_ID, "Password1");

        verify(notificationService, never()).send(any());
    }

    @Test
    @DisplayName("🔴 Redis 挂了 → 不发（往少的方向偏），而且不抛")
    void 节流判断失败时不发() {
        when(redisService.increment(anyString(), anyLong()))
                .thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> notifier.onPasswordVerified(MEMBER_ID, "Password1"));
        verify(notificationService, never()).send(any());
    }

    @Test
    @DisplayName("🔴 发站内信本身炸了也不能冒到登录上")
    void 发送失败不影响登录() {
        Mockito.doThrow(new IllegalStateException("notify down"))
                .when(notificationService).send(any());

        assertDoesNotThrow(() -> notifier.onPasswordVerified(MEMBER_ID, "Password1"));
    }

    @Test
    @DisplayName("开关关掉时什么都不做；memberId 为空时也不做")
    void 开关与空值() {
        ReflectionTestUtils.setField(notifier, "enabled", false);
        notifier.onPasswordVerified(MEMBER_ID, "Password1");

        ReflectionTestUtils.setField(notifier, "enabled", true);
        notifier.onPasswordVerified(null, "Password1");

        verify(notificationService, never()).send(any());
    }
}
