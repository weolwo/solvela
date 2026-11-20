package solvela.admin.module.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.admin.module.notification.domain.form.NotificationTemplateSaveForm;
import solvela.admin.module.notification.domain.vo.NotificationTemplateVO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.EnableStatusEnum;
import solvela.enums.NotificationCategoryEnum;
import solvela.notification.NotificationTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 通知模板的 Form / VO 与实体互转。
 *
 * <p>🔴 {@link SolvelaBeanUtil#copy} 对类型不匹配的属性<b>静默跳过</b>。实体以后改了某个字段的类型
 * 而 Form / VO 没跟着改，这里会红，而不是管理端悄悄拿到一个 null。
 */
@DisplayName("通知模板 Form / VO 映射")
class NotificationTemplateFormMappingTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 20, 10, 0);

    @Test
    @DisplayName("实体 → VO：每个字段都带过去，status 仍是枚举（JSON 里是 1 / 0）")
    void 实体到VO不丢字段() {
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateCode("CONTACT_BOUND");
        t.setVersion(3);
        t.setCategory(NotificationCategoryEnum.SYSTEM);
        t.setTitleTemplate("账号绑定变更提醒");
        t.setContentTemplate("你的账号刚刚绑定了${contactType}");
        t.setParamKeys("[\"contactType\"]");
        t.setStatus(EnableStatusEnum.DISABLED);
        t.setCreateBy("ops");
        t.setCreateTime(T.minusDays(1));
        t.setUpdateBy("ops2");
        t.setUpdateTime(T);

        NotificationTemplateVO vo = SolvelaBeanUtil.copy(t, NotificationTemplateVO.class);

        assertEquals(t.getTemplateCode(), vo.getTemplateCode());
        assertEquals(t.getVersion(), vo.getVersion());
        assertEquals(t.getCategory(), vo.getCategory());
        assertEquals(t.getTitleTemplate(), vo.getTitleTemplate());
        assertEquals(t.getContentTemplate(), vo.getContentTemplate());
        assertEquals(t.getParamKeys(), vo.getParamKeys());
        assertEquals(t.getStatus(), vo.getStatus());
        assertEquals(t.getCreateBy(), vo.getCreateBy());
        assertEquals(t.getCreateTime(), vo.getCreateTime());
        assertEquals(t.getUpdateBy(), vo.getUpdateBy());
        assertEquals(t.getUpdateTime(), vo.getUpdateTime());
    }

    @Test
    @DisplayName("Form → 实体：运营填的字段都带过去；版本号、状态、审计字段一律为空（分别交给服务端与 DDL）")
    void Form到实体只带运营能填的() {
        NotificationTemplateSaveForm form = new NotificationTemplateSaveForm();
        form.setTemplateCode("CONTACT_BOUND");
        form.setCategory(NotificationCategoryEnum.SYSTEM);
        form.setTitleTemplate("账号绑定变更提醒");
        form.setContentTemplate("你的账号刚刚绑定了${contactType}");
        form.setParamKeys("[\"contactType\"]");

        NotificationTemplate t = SolvelaBeanUtil.copy(form, NotificationTemplate.class);

        assertEquals(form.getTemplateCode(), t.getTemplateCode());
        assertEquals(form.getCategory(), t.getCategory());
        assertEquals(form.getTitleTemplate(), t.getTitleTemplate());
        assertEquals(form.getContentTemplate(), t.getContentTemplate());
        assertEquals(form.getParamKeys(), t.getParamKeys());
        assertNull(t.getVersion(), "版本号由服务端取最大版本 + 1");
        assertNull(t.getStatus(), "新版本一律启用：status 留空，INSERT 时取 DDL 默认值 1");
        assertNull(t.getCreateTime());
        assertNull(t.getCreateBy());
    }
}
