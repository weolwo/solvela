package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.enums.NotificationCategoryEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.NotificationTemplate;
import solvela.notification.dao.NotificationTemplateDao;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.domain.dto.MemberNotificationDTO;
import solvela.notification.domain.dto.MemberNotificationDetailDTO;
import solvela.notification.domain.query.MemberNotificationQuery;
import solvela.notification.service.NotificationInboxService;
import solvela.notification.service.NotificationPreferenceService;
import solvela.notification.service.NotificationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知链路的<b>真库</b>验证：发一条、读回来、看渲染。
 *
 * <h3>为什么是 @SpringBootTest 而不是 Mockito</h3>
 * {@code NotificationServiceTest} 已经用 mock 把编排层的分支覆盖过了。
 * 这里要验的是另一件事：<b>真的 Spring 上下文 + 真的 MySQL</b> 下，
 * 模板查得到、参数渲染得出来、行落得进去、再查得回来。
 *
 * <p>本模块其它 {@code @SpringBootTest} 也都是打在开发库上的（见 {@code DeviceDispositionTest}
 * 等），这条只是跟着同一个做法。
 *
 * <h3>🔴 它验的核心是「模板改版不篡改历史」</h3>
 * 这是整个设计的地基：{@code t_member_notification} 只存
 * 「模板编码 + 版本号 + 参数」，正文是读的时候现渲染的。如果渲染时取的是
 * <b>最新版</b>而不是<b>发送当时那一版</b>，运营改一次文案，所有历史通知的
 * 显示就跟着变了 —— 在金额/奖品类消息上那是事故级的。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
class NotificationLiveTest {

    /** 造数专用会员号。刻意用一个不可能撞上真实发号区间的值 */
    private static final Long MEMBER_ID = 9_900_000_001L;

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationInboxService notificationInboxService;
    @Autowired
    private NotificationTemplateDao notificationTemplateDao;
    @Autowired
    private NotificationPreferenceService notificationPreferenceService;

    @Test
    @DisplayName("真库：发一条账号受限通知，读回来，正文按模板渲染")
    void 发一条并读回来() {
        long before = notificationInboxService.countUnread(MEMBER_ID);

        boolean sent = notificationService.send(
                NotifyRequest.of(NotificationTemplateEnum.ACCOUNT_LIMITED, MEMBER_ID)
                        .param("limitType", "登录")
                        .param("unlockTime", "2026-09-15 07:30")
                        .bizRefId("LIVE-TEST")
                        .build());

        assertTrue(sent, "发送失败 —— 多半是 t_notification_template 里没有 ACCOUNT_LIMITED 的启用版本");
        assertEquals(before + 1, notificationInboxService.countUnread(MEMBER_ID), "未读数应当 +1");

        MemberNotificationQuery query = new MemberNotificationQuery();
        query.setMemberId(MEMBER_ID);
        List<MemberNotificationDTO> list =
                notificationInboxService.queryPage(new Page<>(1, 5), query);
        assertFalse(list.isEmpty());

        MemberNotificationDTO latest = list.get(0);
        assertEquals(NotificationTemplateEnum.ACCOUNT_LIMITED.getCode(), latest.getTemplateCode());
        // SYSTEM 分类是从模板抄下来的快照。它同时意味着这条通知【关不掉】
        assertEquals(NotificationCategoryEnum.SYSTEM, latest.getCategory());
        // summary 是发送时就渲染好的 —— 列表页因此不用查模板、不用渲染
        assertTrue(latest.getSummary().contains("登录"), "摘要里应该有渲染后的 limitType，实际: " + latest.getSummary());
        assertFalse(latest.getSummary().contains("${"), "摘要里还有没替换掉的占位符: " + latest.getSummary());

        MemberNotificationDetailDTO detail = notificationInboxService.detail(latest.getId(), MEMBER_ID);
        assertNotNull(detail);
        assertTrue(detail.getContent().contains("2026-09-15 07:30"), "正文: " + detail.getContent());
        assertFalse(detail.getContent().contains("${"), "正文里还有没替换掉的占位符: " + detail.getContent());

        // 🔴 越权：换一个会员号去读同一条，必须什么都拿不到
        assertNotNull(detail, "前置断言");
        org.junit.jupiter.api.Assertions.assertNull(
                notificationInboxService.detail(latest.getId(), MEMBER_ID + 1),
                "拿别人的会员号读到了内容 —— 这是越权");
    }

    @Test
    @DisplayName("🔴 真库：模板改版之后，历史通知仍按发送当时那一版渲染")
    void 改版不篡改历史() {
        // ① 用当前最新版发一条
        notificationService.send(
                NotifyRequest.of(NotificationTemplateEnum.ACCOUNT_LIMITED, MEMBER_ID)
                        .param("limitType", "改版前")
                        .param("unlockTime", "2026-09-15 08:00")
                        .bizRefId("LIVE-VERSION-TEST")
                        .build());

        MemberNotificationQuery query = new MemberNotificationQuery();
        query.setMemberId(MEMBER_ID);
        MemberNotificationDTO old = notificationInboxService.queryPage(new Page<>(1, 1), query).get(0);
        String contentBefore = notificationInboxService.detail(old.getId(), MEMBER_ID).getContent();

        // ② 发一个新版本，措辞完全不同。
        //
        // 🔴 标记必须【每次运行唯一】。第一版测试写死了「改版后」三个字，结果上一轮
        //    跑挂时 finally 没执行、库里残留了一个带这三个字的版本，下一轮的
        //    contentBefore 就本来含它 —— 断言挂了，但挂的是【测试自己不够健壮】，
        //    真正的核心断言（contentBefore == contentAfter）其实是过的。
        //    用唯一标记之后，这条测试不再依赖库里是干净的。
        String marker = "V-MARK-" + System.nanoTime();

        NotificationTemplate latest =
                notificationTemplateDao.selectLatestEnabled(NotificationTemplateEnum.ACCOUNT_LIMITED.getCode());
        NotificationTemplate next = new NotificationTemplate();
        next.setTemplateCode(latest.getTemplateCode());
        next.setVersion(latest.getVersion() + 1);
        next.setCategory(latest.getCategory());
        next.setTitleTemplate(marker + " 账号受限");
        next.setContentTemplate(marker + " 您的账号因「${limitType}」受限，${unlockTime} 恢复。");
        next.setParamKeys(latest.getParamKeys());
        next.setStatus(1);
        notificationTemplateDao.insert(next);

        try {
            // ③ 老通知再读一次 —— 内容必须【一个字都没变】
            String contentAfter = notificationInboxService.detail(old.getId(), MEMBER_ID).getContent();
            assertEquals(contentBefore, contentAfter,
                    "模板改版之后历史通知的措辞变了 —— 这正是 template_version 要防的事故");
            assertFalse(contentAfter.contains(marker),
                    "历史通知被新模板污染了（渲染时取了最新版而不是发送当时那一版）: " + contentAfter);
        } finally {
            // 造出来的版本收掉，别污染开发库。
            // ⚠️ 用 deleteVersion 而不是 deleteById —— 复合主键，基类方法生成不出来。
            //    这个坑就是本测试第一次跑时炸出来的
            notificationTemplateDao.deleteVersion(next.getTemplateCode(), next.getVersion());
        }
    }

    @Test
    @DisplayName("🔴 真库：营销类能被用户关掉，系统类关不掉")
    void 免打扰对SYSTEM无效() {
        try {
            // 全关。注意契约里压根没有 systemEnabled —— 那一档没有开关可拨
            notificationPreferenceService.save(MEMBER_ID, false, false);

            assertFalse(notificationPreferenceService.accepts(MEMBER_ID, NotificationCategoryEnum.MARKETING),
                    "营销类关掉之后还在放行");
            assertFalse(notificationPreferenceService.accepts(MEMBER_ID, NotificationCategoryEnum.TRADE),
                    "交易类关掉之后还在放行");
            // 🔴 账号被冻结、条款变更这类东西不该能被静音
            assertTrue(notificationPreferenceService.accepts(MEMBER_ID, NotificationCategoryEnum.SYSTEM),
                    "SYSTEM 被静音了 —— 用户会收不到「你被冻结了」");

            // 端到端：关了营销之后，一条 MARKETING 通知不该落库
            long before = notificationInboxService.countUnread(MEMBER_ID);
            boolean marketingSent = notificationService.send(
                    NotifyRequest.of(NotificationTemplateEnum.PRIZE_WON, MEMBER_ID)
                            .param("prizeName", "被免打扰拦下的奖")
                            .param("amount", "1")
                            .build());
            assertFalse(marketingSent, "营销类关掉之后仍然发出去了");
            assertEquals(before, notificationInboxService.countUnread(MEMBER_ID), "被拦下的通知不该落库");

            // 而 SYSTEM 照发
            boolean systemSent = notificationService.send(
                    NotifyRequest.of(NotificationTemplateEnum.ACCOUNT_LIMITED, MEMBER_ID)
                            .param("limitType", "免打扰验证")
                            .param("unlockTime", "2026-09-15 09:00")
                            .build());
            assertTrue(systemSent, "SYSTEM 类被免打扰拦了 —— 那一档本该关不掉");
            assertEquals(before + 1, notificationInboxService.countUnread(MEMBER_ID));
        } finally {
            // 恢复默认，别让造数影响后续
            notificationPreferenceService.save(MEMBER_ID, true, true);
        }
    }
}
