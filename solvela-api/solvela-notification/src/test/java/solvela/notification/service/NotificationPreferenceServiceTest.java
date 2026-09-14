package solvela.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.NotificationCategoryEnum;
import solvela.notification.MemberNotificationPreference;
import solvela.notification.dao.MemberNotificationPreferenceDao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 免打扰偏好。
 *
 * <h3>这个类只有一个真正危险的判断</h3>
 * <b>查不到偏好行 ≠ 关闭。</b> 偏好行是懒创建的，绝大多数用户压根没有这一行。
 * 把「查不到」当成关闭，表现是<b>上线当天全体存量用户再也收不到任何通知</b>，
 * 而且不报错、没人发现。下面前三条测试守的就是这一件事的三个侧面。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPreferenceServiceTest {

    private static final Long MEMBER_ID = 900001L;

    @Mock
    private MemberNotificationPreferenceDao dao;

    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(dao);
    }

    @Test
    @DisplayName("🔴 没有偏好行 = 全部接收，不是全部关闭")
    void 没有偏好行默认全收() {
        when(dao.selectById(anyLong())).thenReturn(null);

        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.TRADE));
        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.MARKETING));
    }

    @Test
    @DisplayName("查偏好炸了也放行 —— 宁可多发一条营销，也不要吞掉发货通知")
    void 查询异常也放行() {
        when(dao.selectById(anyLong())).thenThrow(new RuntimeException("库挂了"));

        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.TRADE));
    }

    @Test
    @DisplayName("列是 null 也当成开 —— 列是后加的，存量行上可能没值")
    void 空值当成开() {
        MemberNotificationPreference preference = new MemberNotificationPreference();
        preference.setMemberId(MEMBER_ID);
        preference.setTradeEnabled(null);
        preference.setMarketingEnabled(null);
        when(dao.selectById(anyLong())).thenReturn(preference);

        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.TRADE));
        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.MARKETING));
    }

    @Test
    @DisplayName("🔴 SYSTEM 关不掉，而且压根不查库")
    void SYSTEM不可关() {
        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.SYSTEM));

        // 不可关的分类连库都不用查 —— 这既是性能，也是「没人能改变这个结果」的保证
        verify(dao, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("关掉的分类确实拦得住，没关的不受影响")
    void 关掉的才拦() {
        MemberNotificationPreference preference = new MemberNotificationPreference();
        preference.setMemberId(MEMBER_ID);
        preference.setTradeEnabled(1);
        preference.setMarketingEnabled(0);
        when(dao.selectById(anyLong())).thenReturn(preference);

        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.TRADE));
        assertFalse(service.accepts(MEMBER_ID, NotificationCategoryEnum.MARKETING));
        // 即使显式关了别的，SYSTEM 也照发
        assertTrue(service.accepts(MEMBER_ID, NotificationCategoryEnum.SYSTEM));
    }
}
