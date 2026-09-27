package solvela.member.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import solvela.base.mail.MailService;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiCipher;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.notification.service.NotificationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新设备登录提醒：什么时候发、什么时候不发。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 注册后一直用注册会话、从没「登录」过的会员，在另一台设备上登录 → <b>要发</b>。
 *       初版判据「以前登录过」在这里整个失效，上线实测才发现 —— 这正是盗号的典型场景；</li>
 *   <li>在注册那台上重新登录 → 不发；</li>
 *   <li>这台设备以前登录成功过 → 不发；</li>
 *   <li>不知道注册设备（老会员 / Redis 丢了）→ 退回初版判据；</li>
 *   <li>记录注册设备失败不影响注册。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewDeviceLoginNotifierTest {

    private static final long MEMBER_ID = 7002335408L;
    private static final String REG_DEVICE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_DEVICE = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String KEY = "reg-device-key";

    @Mock
    private MemberLoginLogDao loginLogDao;
    @Mock
    private MemberAuthDao memberAuthDao;
    @Mock
    private PiiCipher piiCipher;
    @Mock
    private MailService mailService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RedisService redisService;
    @Mock
    private AsyncTaskExecutor sendExecutor;

    @InjectMocks
    private NewDeviceLoginNotifier notifier;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notifier, "sendExecutor", sendExecutor);
        ReflectionTestUtils.setField(notifier, "enabled", true);
        when(redisService.generateRedisKey(anyString(), anyString())).thenReturn(KEY);
    }

    private void login(String deviceId) {
        notifier.onLoginSucceeded(MEMBER_ID, deviceId, "H5", "10.0.0.1");
    }

    private void verifySent() {
        verify(sendExecutor).execute(any(Runnable.class));
    }

    private void verifyNotSent() {
        verify(sendExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("🔴 注册后从没登录过，在另一台设备上登录 → 发提醒")
    void 注册会话一直在用_别处登录要提醒() {
        when(redisService.get(KEY)).thenReturn(REG_DEVICE);
        when(loginLogDao.existsSuccessfulLogin(anyLong(), anyInt())).thenReturn(false);
        when(loginLogDao.existsSuccessfulLoginOnDevice(anyLong(), anyString(), anyInt())).thenReturn(false);

        login(OTHER_DEVICE);

        verifySent();
    }

    @Test
    @DisplayName("在注册那台上重新登录 → 不发")
    void 注册设备上重新登录不提醒() {
        when(redisService.get(KEY)).thenReturn(REG_DEVICE);

        login(REG_DEVICE);

        verifyNotSent();
    }

    @Test
    @DisplayName("这台设备以前登录成功过 → 不发")
    void 老设备不提醒() {
        when(redisService.get(KEY)).thenReturn(REG_DEVICE);
        when(loginLogDao.existsSuccessfulLoginOnDevice(eq(MEMBER_ID), eq(OTHER_DEVICE), anyInt())).thenReturn(true);

        login(OTHER_DEVICE);

        verifyNotSent();
    }

    @Test
    @DisplayName("不知道注册设备、也从没登录过 → 退回初版判据，不发")
    void 注册设备未知_首登不提醒() {
        when(redisService.get(KEY)).thenReturn(null);
        when(loginLogDao.existsSuccessfulLogin(anyLong(), anyInt())).thenReturn(false);

        login(OTHER_DEVICE);

        verifyNotSent();
    }

    @Test
    @DisplayName("不知道注册设备、以前在别处登录过、这台是新的 → 发")
    void 注册设备未知_老会员新设备提醒() {
        when(redisService.get(KEY)).thenReturn(null);
        when(loginLogDao.existsSuccessfulLogin(anyLong(), anyInt())).thenReturn(true);
        when(loginLogDao.existsSuccessfulLoginOnDevice(anyLong(), anyString(), anyInt())).thenReturn(false);

        login(OTHER_DEVICE);

        verifySent();
    }

    @Test
    @DisplayName("没有设备号 → 不发")
    void 没有设备号不提醒() {
        login(null);

        verifyNotSent();
    }

    @Test
    @DisplayName("注册时记住设备号，TTL 400 天")
    void 记住注册设备() {
        notifier.rememberRegistrationDevice(MEMBER_ID, REG_DEVICE);

        verify(redisService).set(KEY, REG_DEVICE, 400L * 24 * 3600);
    }

    @Test
    @DisplayName("记录注册设备时 Redis 出错 → 吞掉，不影响注册")
    void 记录失败不抛() {
        doThrow(new RuntimeException("redis down")).when(redisService).set(anyString(), anyString(), anyLong());

        notifier.rememberRegistrationDevice(MEMBER_ID, REG_DEVICE);
    }
}
