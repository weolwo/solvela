package solvela.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.NotificationCategoryEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.NotificationTemplate;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.domain.RenderedNotification;
import solvela.notification.spi.NotificationSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触达编排层。
 *
 * <h3>这里的每一条都是「不这么写会怎样」</h3>
 * <ul>
 *   <li><b>send() 永不抛异常</b> —— 抛了就意味着「模板没配」能让发货事务回滚，
 *       一个通知配置问题变成一次业务故障；</li>
 *   <li><b>落库的是模板引用不是正文</b> —— 存正文的话，那张会长到亿级的表
 *       每行多几百字节，buffer pool 命中率跟着塌；</li>
 *   <li><b>SYSTEM 分类拦不住</b> —— 账号被冻结这种事不该能被静音；</li>
 *   <li><b>查不到偏好 = 全部接收</b> —— 反了的话上线当天全体存量用户
 *       再也收不到任何通知，而且不报错。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final Long MEMBER_ID = 900001L;

    @Mock
    private NotificationTemplateService notificationTemplateService;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;
    @Mock
    private NotificationSender sender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationTemplateService, notificationPreferenceService,
                List.of(sender));
        when(notificationPreferenceService.accepts(anyLong(), any())).thenReturn(true);
    }

    private NotificationTemplate template(String title, String content) {
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateCode(NotificationTemplateEnum.PRIZE_WON.getCode());
        t.setVersion(3);
        t.setCategory(NotificationCategoryEnum.MARKETING);
        t.setTitleTemplate(title);
        t.setContentTemplate(content);
        t.setStatus(1);
        return t;
    }

    private NotifyRequest request() {
        return NotifyRequest.of(NotificationTemplateEnum.PRIZE_WON, MEMBER_ID)
                .param("prizeName", "iPhone 15 Pro")
                .param("amount", "1")
                .bizRefId("DRAW-123")
                .build();
    }

    // ------------------------------------------------------------------ 渲染与落库

    @Test
    @DisplayName("渲染后交给渠道，且带着模板版本与参数（而不是只有正文）")
    void 渲染并带上模板引用() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenReturn(template("恭喜您中奖啦", "您获得的 ${prizeName} ×${amount} 已发放"));

        assertTrue(service.send(request()));

        ArgumentCaptor<RenderedNotification> captor = ArgumentCaptor.forClass(RenderedNotification.class);
        verify(sender).send(captor.capture());
        RenderedNotification rendered = captor.getValue();

        assertEquals("恭喜您中奖啦", rendered.title());
        assertEquals("您获得的 iPhone 15 Pro ×1 已发放", rendered.content());

        // 🔴 这两个才是落库的东西。没有 templateVersion，改模板就会追溯篡改历史通知
        assertEquals(3, rendered.templateVersion());
        assertTrue(rendered.paramsJson().contains("iPhone 15 Pro"));
        assertEquals("DRAW-123", rendered.bizRefId());
    }

    @Test
    @DisplayName("摘要在发送时就渲染好，列表页才能零渲染")
    void 摘要发送时算好() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenReturn(template("标题", "您获得的 ${prizeName} 已发放"));

        service.send(request());

        ArgumentCaptor<RenderedNotification> captor = ArgumentCaptor.forClass(RenderedNotification.class);
        verify(sender).send(captor.capture());
        assertEquals("您获得的 iPhone 15 Pro 已发放", captor.getValue().summary());
    }

    @Test
    @DisplayName("超长正文的摘要被截断到 128，不是报错")
    void 摘要超长截断() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenReturn(template("标题", "x".repeat(500)));

        service.send(request());

        ArgumentCaptor<RenderedNotification> captor = ArgumentCaptor.forClass(RenderedNotification.class);
        verify(sender).send(captor.capture());
        // 摘要是给列表页看一眼的，截了不影响正确性；为一条摘要太长就不发通知，
        // 是拿次要目标伤害主要目标
        assertEquals(128, captor.getValue().summary().length());
    }

    // ------------------------------------------------------------------ 永不抛异常

    @Test
    @DisplayName("模板没配：不发、不抛异常")
    void 模板缺失不抛异常() {
        when(notificationTemplateService.getLatestEnabled(anyString())).thenReturn(null);

        // 🔴 抛了就意味着「运营忘配一个模板」能让发货事务回滚
        assertFalse(assertDoesNotThrow(() -> service.send(request())));
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("渠道自己炸了：send 仍然不抛异常")
    void 渠道异常不外抛() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenReturn(template("标题", "正文"));
        org.mockito.Mockito.doThrow(new RuntimeException("库挂了")).when(sender).send(any());

        assertFalse(assertDoesNotThrow(() -> service.send(request())));
    }

    @Test
    @DisplayName("模板查询本身炸了：send 仍然不抛异常")
    void 模板查询异常不外抛() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenThrow(new RuntimeException("连接池满了"));

        assertFalse(assertDoesNotThrow(() -> service.send(request())));
    }

    // ------------------------------------------------------------------ 免打扰

    @Test
    @DisplayName("用户关掉了这个分类：不投递")
    void 免打扰拦下() {
        when(notificationTemplateService.getLatestEnabled(anyString()))
                .thenReturn(template("标题", "正文"));
        when(notificationPreferenceService.accepts(MEMBER_ID, NotificationCategoryEnum.MARKETING))
                .thenReturn(false);

        assertFalse(service.send(request()));
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("缺参数只告警不拦截 —— 否则模板加个占位符就让老调用方全发不出去")
    void 缺参数不拦截() {
        NotificationTemplate t = template("标题", "您获得的 ${prizeName} ${missingKey}");
        t.setParamKeys("[\"prizeName\",\"missingKey\"]");
        when(notificationTemplateService.getLatestEnabled(anyString())).thenReturn(t);

        assertTrue(service.send(request()));

        ArgumentCaptor<RenderedNotification> captor = ArgumentCaptor.forClass(RenderedNotification.class);
        verify(sender).send(captor.capture());
        // SolvelaTemplateUtil 的口径：解析不到的占位符【原样保留】。
        // 这条断言把那个口径钉住 —— 哪天它改成「替换成空串」，这里会失败，
        // 而那个改动会悄悄影响所有历史通知的渲染
        assertTrue(captor.getValue().content().contains("${missingKey}"));
    }
}
