package solvela.member.operationlimit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.module.redis.RedisService;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.member.MemberOperationLimit;
import solvela.member.operationlimit.MemberOperationLimitProperties;
import solvela.member.operationlimit.dao.MemberOperationLimitDao;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员操作限制：连续失败计数与解冻。
 *
 * <h3>三条都是「不这么写会怎样」</h3>
 * <ul>
 *   <li><b>已经在限制中就不再叠加。</b>否则被限期间的每一次尝试都把到期时间往后推，
 *       变成「越试越久」—— 用户永远等不到自动解除，而他并不知道是自己的重试造成的；</li>
 *   <li><b>限制落库之后要清掉 Redis 计数。</b>留着的话解冻后第一次失败就又被限，
 *       表现是「刚解封又被锁」，客服解一次锁一次；</li>
 *   <li><b>解冻同样要清计数</b>，理由同上。</li>
 * </ul>
 *
 * <h3>计数在 Redis、限制在 MySQL 是刻意的</h3>
 * 计数高频、可丢（丢了最坏是攻击者多试几次）；限制低频、不可丢、要给客服看要能追溯。
 * 两者故障时的降级方向也不同：Redis 挂了退化成「不限制」，MySQL 挂了登录本来也走不下去。
 *
 * @Author alaric
 * @Date 2026-09-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberOperationLimitServiceTest {

    private static final Long MEMBER_ID = 900001L;
    private static final MemberOperationTypeEnum LOGIN = MemberOperationTypeEnum.LOGIN;

    @Mock
    private MemberOperationLimitDao memberOperationLimitDao;
    @Mock
    private RedisService redisService;
    @Mock
    private NotificationService notificationService;

    private MemberOperationLimitProperties properties;
    private MemberOperationLimitService service;

    @BeforeEach
    void setUp() {
        properties = new MemberOperationLimitProperties();
        service = new MemberOperationLimitService(memberOperationLimitDao, properties, redisService,
                notificationService);
        when(redisService.generateRedisKey(anyString(), anyString())).thenReturn("k");
        when(memberOperationLimitDao.selectActive(anyLong(), any(), any())).thenReturn(null);
    }

    // ------------------------------------------------------------------ 记失败

    @Test
    @DisplayName("未达阈值：只计数，不落限制")
    void 未达阈值不落限制() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getFailMaxTimes() - 1);

        assertNull(service.recordFail(MEMBER_ID, LOGIN, "连续登录失败"));
        verify(memberOperationLimitDao, never()).insert(any(MemberOperationLimit.class));
        // 没落限制就不该发通知 —— 否则用户每输错一次密码都收一条「账号受限」
        verify(notificationService, never()).send(any(NotifyRequest.class));
    }

    @Test
    @DisplayName("达到阈值：落一行限制，并把计数清掉")
    void 达阈值落限制并清计数() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getFailMaxTimes());

        MemberOperationLimit limit = service.recordFail(MEMBER_ID, LOGIN, "连续登录失败");

        assertNotNull(limit);
        ArgumentCaptor<MemberOperationLimit> captor = ArgumentCaptor.forClass(MemberOperationLimit.class);
        verify(memberOperationLimitDao).insert(captor.capture());
        MemberOperationLimit saved = captor.getValue();
        assertEquals(MemberOperationLimitStatusEnum.LOCKED, saved.getStatus());
        assertEquals("连续登录失败", saved.getReason(), "写进表里给客服看的人话，不能丢");
        assertTrue(saved.getExpireTime().isAfter(saved.getLockTime()));
        // 计数已经兑现成一条限制，留着它会让解冻后「一次失败就又被限」
        verify(redisService).delete(anyString());
    }

    @Test
    @DisplayName("🔴 已经在限制中：返回原来那条，绝不再插一条把到期时间往后推")
    void 限制中不叠加() {
        when(redisService.increment(anyString(), anyLong())).thenReturn(99L);
        MemberOperationLimit active = limitExpiringIn(600);
        when(memberOperationLimitDao.selectActive(anyLong(), any(), any())).thenReturn(active);

        MemberOperationLimit result = service.recordFail(MEMBER_ID, LOGIN, "连续登录失败");

        // 叠加的表现是「越试越久」，用户永远等不到自动解除，而他不知道是自己重试造成的
        assertEquals(active.getExpireTime(), result.getExpireTime());
        verify(memberOperationLimitDao, never()).insert(any(MemberOperationLimit.class));
    }

    @Test
    @DisplayName("阈值配成 0 或负数 = 关闭这个功能，连计数都不做")
    void 阈值关闭() {
        properties.setFailMaxTimes(0);

        assertNull(service.recordFail(MEMBER_ID, LOGIN, "连续登录失败"));
        verify(redisService, never()).increment(anyString(), anyLong());
    }

    @Test
    @DisplayName("参数为空一律当没发生，不要抛异常打断调用方的主流程")
    void 空参数不抛异常() {
        assertNull(service.recordFail(null, LOGIN, "x"));
        assertNull(service.recordFail(MEMBER_ID, null, "x"));
        assertNull(service.getActiveLimit(null, LOGIN));
        assertNull(service.getActiveLimit(MEMBER_ID, null));
        verify(redisService, never()).increment(anyString(), anyLong());
    }

    // ------------------------------------------------------------------ 查限制 / 清计数

    @Test
    @DisplayName("是否受限只看数据库的时间比较，不看 status 列")
    void 是否受限只看时间() {
        MemberOperationLimit active = limitExpiringIn(300);
        when(memberOperationLimitDao.selectActive(anyLong(), any(), any())).thenReturn(active);

        // status 列是给人看的，settleExpired 漏跑几次不该影响业务判断
        assertNotNull(service.getActiveLimit(MEMBER_ID, LOGIN));
        verify(memberOperationLimitDao).selectActive(eqMemberId(), any(), any());
    }

    @Test
    @DisplayName("清计数：删的是「操作类型 + 会员」那一个键，不是整个会员")
    void 清计数的粒度() {
        service.clearFail(MEMBER_ID, LOGIN);

        // 粒度太粗的话，改密码失败会把登录失败的计数一起清掉，两种限制互相抵消
        verify(redisService).generateRedisKey(anyString(),
                org.mockito.ArgumentMatchers.eq(LOGIN.getValue() + ":" + MEMBER_ID));
        verify(redisService).delete(anyString());
    }

    // ------------------------------------------------------------------ 解冻

    @Test
    @DisplayName("解冻成功：同时清掉计数")
    void 解冻要清计数() {
        when(memberOperationLimitDao.unlock(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);

        assertTrue(service.unlock(MEMBER_ID, LOGIN, MemberOperationUnlockTypeEnum.MANUAL, "客服A", "用户来电"));

        // 解了锁却留着计数，等于「解冻后再错一次立刻又被限」—— 客服解一次锁一次
        verify(redisService).delete(anyString());
    }

    @Test
    @DisplayName("没有生效中的限制：幂等返回 false，不去动计数")
    void 解冻幂等() {
        when(memberOperationLimitDao.unlock(anyLong(), any(), any(), any(), any(), any())).thenReturn(0);

        assertFalse(service.unlock(MEMBER_ID, LOGIN, MemberOperationUnlockTypeEnum.MANUAL, "客服A", "用户来电"));
        verify(redisService, never()).delete(anyString());
    }

    @Test
    @DisplayName("解冻参数为空：返回 false，不发 SQL")
    void 解冻空参数() {
        assertFalse(service.unlock(null, LOGIN, MemberOperationUnlockTypeEnum.MANUAL, "客服A", "x"));
        assertFalse(service.unlock(MEMBER_ID, null, MemberOperationUnlockTypeEnum.MANUAL, "客服A", "x"));
        verify(memberOperationLimitDao, never()).unlock(anyLong(), any(), any(), any(), any(), any());
    }

    private Long eqMemberId() {
        return org.mockito.ArgumentMatchers.eq(MEMBER_ID);
    }

    private MemberOperationLimit limitExpiringIn(long seconds) {
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(MEMBER_ID);
        limit.setOperationType(LOGIN);
        limit.setStatus(MemberOperationLimitStatusEnum.LOCKED);
        limit.setExpireTime(LocalDateTime.now().plusSeconds(seconds));
        return limit;
    }

    // ------------------------------------------------------------------ 限制通知

    @Test
    @DisplayName("触发限制时发一条站内信，且不把内部 reason 透给用户")
    void 触发限制发通知() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getFailMaxTimes());

        service.recordFail(MEMBER_ID, LOGIN, "连续5次密码错误");

        ArgumentCaptor<NotifyRequest> captor = ArgumentCaptor.forClass(NotifyRequest.class);
        verify(notificationService).send(captor.capture());
        NotifyRequest request = captor.getValue();

        assertEquals(MEMBER_ID, request.memberId());
        assertEquals(NotificationTemplateEnum.ACCOUNT_LIMITED, request.template());
        assertEquals(LOGIN.getDesc(), request.params().get("limitType"));
        assertNotNull(request.params().get("unlockTime"));

        // 🔴 reason 是给客服看的内部措辞，可能含「连续5次密码错误」这类对攻击者
        //    有用的信息。用户只需要知道受限类型和恢复时间。
        //    这条断言守的是「以后别顺手把 reason 加进参数」
        assertFalse(request.params().containsValue("连续5次密码错误"));
    }

    @Test
    @DisplayName("已在限制中：不重复发通知")
    void 已在限制中不重复发通知() {
        when(redisService.increment(anyString(), anyLong()))
                .thenReturn((long) properties.getFailMaxTimes());
        MemberOperationLimit active = new MemberOperationLimit();
        active.setMemberId(MEMBER_ID);
        active.setExpireTime(LocalDateTime.now().plusMinutes(10));
        when(memberOperationLimitDao.selectActive(anyLong(), any(), any())).thenReturn(active);

        service.recordFail(MEMBER_ID, LOGIN, "连续登录失败");

        // 被限期间用户会反复重试，每次都发一条就是刷屏
        verify(notificationService, never()).send(any(NotifyRequest.class));
    }
}
