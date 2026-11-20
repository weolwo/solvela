package solvela.admin.module.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.admin.module.notification.domain.form.AnnouncementSaveForm;
import solvela.admin.module.notification.domain.vo.AnnouncementVO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.AnnouncementStatusEnum;
import solvela.enums.NotificationCategoryEnum;
import solvela.notification.Announcement;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 公告的 Form / VO 与实体互转。
 *
 * <p>🔴 {@link SolvelaBeanUtil#copy} 对类型不匹配的属性<b>静默跳过</b>。以后实体改了某个字段的类型
 * 而 Form / VO 没跟着改，这里会红，而不是管理端悄悄拿到一个 null。
 */
@DisplayName("公告 Form / VO 映射")
class AnnouncementFormMappingTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Test
    @DisplayName("实体 → VO：每个字段都带过去")
    void 实体到VO不丢字段() {
        Announcement a = new Announcement();
        a.setId(7L);
        a.setTitle("停机维护");
        a.setContent("今晚 0 点");
        a.setCategory(NotificationCategoryEnum.SYSTEM);
        a.setForceAck(true);
        a.setAudienceType("REGISTER_BEFORE");
        a.setAudienceRule("{\"before\":\"2026-11-01 00:00:00\"}");
        a.setPublishTime(T);
        a.setExpireTime(T.plusDays(1));
        a.setStatus(AnnouncementStatusEnum.OFFLINE);
        a.setCreateBy("ops");
        a.setCreateTime(T.minusDays(1));
        a.setUpdateBy("ops2");
        a.setUpdateTime(T.minusHours(1));

        AnnouncementVO vo = SolvelaBeanUtil.copy(a, AnnouncementVO.class);

        assertEquals(a.getId(), vo.getId());
        assertEquals(a.getTitle(), vo.getTitle());
        assertEquals(a.getContent(), vo.getContent());
        assertEquals(a.getCategory(), vo.getCategory());
        assertEquals(a.getForceAck(), vo.getForceAck());
        assertEquals(a.getAudienceType(), vo.getAudienceType());
        assertEquals(a.getAudienceRule(), vo.getAudienceRule());
        assertEquals(a.getPublishTime(), vo.getPublishTime());
        assertEquals(a.getExpireTime(), vo.getExpireTime());
        assertEquals(a.getStatus(), vo.getStatus());
        assertEquals(a.getCreateBy(), vo.getCreateBy());
        assertEquals(a.getCreateTime(), vo.getCreateTime());
        assertEquals(a.getUpdateBy(), vo.getUpdateBy());
        assertEquals(a.getUpdateTime(), vo.getUpdateTime());
    }

    @Test
    @DisplayName("Form → 实体：运营填的字段都带过去，审计字段一律为空（交给 DDL）")
    void Form到实体不带审计字段() {
        AnnouncementSaveForm form = new AnnouncementSaveForm();
        form.setId(7L);
        form.setTitle("停机维护");
        form.setContent("今晚 0 点");
        form.setCategory(NotificationCategoryEnum.SYSTEM);
        form.setForceAck(true);
        form.setAudienceType("ALL");
        form.setAudienceRule(null);
        form.setPublishTime(T);
        form.setExpireTime(T.plusDays(1));
        form.setStatus(AnnouncementStatusEnum.PUBLISHED);

        Announcement a = SolvelaBeanUtil.copy(form, Announcement.class);

        assertEquals(form.getId(), a.getId());
        assertEquals(form.getTitle(), a.getTitle());
        assertEquals(form.getContent(), a.getContent());
        assertEquals(form.getCategory(), a.getCategory());
        assertEquals(form.getForceAck(), a.getForceAck());
        assertEquals(form.getAudienceType(), a.getAudienceType());
        assertEquals(form.getPublishTime(), a.getPublishTime());
        assertEquals(form.getExpireTime(), a.getExpireTime());
        assertEquals(form.getStatus(), a.getStatus());
        assertNull(a.getCreateTime(), "create_time 由 DDL 产生");
        assertNull(a.getUpdateTime(), "update_time 由 DDL 产生");
        assertNull(a.getCreateBy());
        assertNull(a.getUpdateBy());
    }
}
