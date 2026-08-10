package net.lab1024.sa.activity.service;

import net.lab1024.sa.activity.dao.ActivityDisplayDao;
import net.lab1024.sa.activity.domain.entity.ActivityDisplay;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.module.support.file.service.FileAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ActivityDisplayService}：把「独立图片字段」和「富文本正文里的图」合并成一份引用集合。
 *
 * <p>这些断言守的是一个会静默发生的事故 —— 漏登记一张图 = 那张图不在 t_file_relation 里 =
 * 孤儿清理任务把它删掉 = 三个月后活动页变叉图且查不出原因。
 *
 * @Date 2026-08-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityDisplayServiceTest {

    @Mock
    private ActivityDisplayDao activityDisplayDao;

    @Mock
    private FileAssetService fileAssetService;

    private ActivityDisplayService service;

    @BeforeEach
    void setUp() {
        service = new ActivityDisplayService();
        ReflectionTestUtils.setField(service, "activityDisplayDao", activityDisplayDao);
        ReflectionTestUtils.setField(service, "fileAssetService", fileAssetService);
    }

    private static ActivityDisplay form(Long activityId) {
        ActivityDisplay form = new ActivityDisplay();
        form.setActivityId(activityId);
        return form;
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedConfirmIds() {
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(fileAssetService).confirm(captor.capture(),
                eq(ActivityDisplayService.BIZ_TYPE), eq(1L));
        return captor.getValue();
    }

    @Test
    @DisplayName("三个独立图片字段和富文本正文里的图，一起进引用集合")
    void collectsBothFieldImagesAndInlineImages() {
        ActivityDisplay form = form(1L);
        form.setMainImageId(10L);
        form.setBgImageId(11L);
        form.setShareImageId(12L);
        form.setRuleContent("<p>规则</p><img src=\"/file/download/20\"><img src=\"/file/download/21\">");
        when(fileAssetService.resolveFileIds(any())).thenReturn(List.of(20L, 21L));

        service.save(form, null);

        assertThat(capturedConfirmIds()).containsExactly(10L, 11L, 12L, 20L, 21L);
    }

    @Test
    @DisplayName("重复的 fileId 只登记一次（主视觉同时被插进正文是常见操作）")
    void deduplicates() {
        ActivityDisplay form = form(1L);
        form.setMainImageId(10L);
        form.setRuleContent("<img src=\"/file/download/10\">");
        when(fileAssetService.resolveFileIds(any())).thenReturn(List.of(10L));

        service.save(form, null);

        assertThat(capturedConfirmIds()).containsExactly(10L);
    }

    @Test
    @DisplayName("引用集合是全量覆盖：上一版引用、这一版删掉的图会自动解除引用")
    void referencesAreFullyReplaced() {
        // confirm 内部是「先清后建」，所以这里只要保证传的是本次的完整集合即可 ——
        // 少传等于解除引用，这正是想要的语义
        ActivityDisplay form = form(1L);
        form.setMainImageId(99L);
        form.setRuleContent("<p>这一版把配图删了</p>");

        service.save(form, null);

        assertThat(capturedConfirmIds()).containsExactly(99L);
        // 正文里没有 img 就不该白跑一次反查
        verify(fileAssetService, never()).resolveFileIds(anyList());
    }

    @Test
    @DisplayName("base64 内联图片后端硬拦 —— 编辑器那边的配置是会被人改回来的")
    void rejectsBase64() {
        ActivityDisplay form = form(1L);
        form.setRuleContent("<img src=\"data:image/png;base64,iVBORw0KGgo=\">");

        assertThatThrownBy(() -> service.save(form, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("base64");
        verify(activityDisplayDao, never()).insert(any(ActivityDisplay.class));
    }

    @Test
    @DisplayName("富文本超长被拒：mediumtext 能装 16MB，但那不该是业务上限")
    void rejectsTooLongRuleContent() {
        ActivityDisplay form = form(1L);
        form.setRuleContent("x".repeat(200_001));

        assertThatThrownBy(() -> service.save(form, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过长");
    }

    @Test
    @DisplayName("activityId 为空直接拒，不会插出一条挂不到任何活动上的孤儿配置")
    void rejectsMissingActivityId() {
        assertThatThrownBy(() -> service.save(form(null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动ID");
    }

    @Test
    @DisplayName("已存在则更新而不是再插一条 —— uk_activity 是唯一键，插第二条会直接报错")
    void updatesWhenExisting() {
        ActivityDisplay existing = form(1L);
        existing.setId(77L);
        when(activityDisplayDao.getByActivityId(1L)).thenReturn(existing);

        ActivityDisplay form = form(1L);
        service.save(form, null);

        assertThat(form.getId()).isEqualTo(77L);
        verify(activityDisplayDao).updateById(form);
        verify(activityDisplayDao, never()).insert(any(ActivityDisplay.class));
    }
}
