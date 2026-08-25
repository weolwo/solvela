package solvela.base.module.support.file.service;

import solvela.base.common.exception.BusinessException;
import solvela.base.module.support.file.dao.FileCategoryDao;
import solvela.base.module.support.file.dao.FileDao;
import solvela.base.module.support.file.domain.entity.FileCategoryEntity;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileCategoryService}：内置分类保护与拖拽排序。
 *
 * @Date 2026-08-10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileCategoryServiceTest {

    @Mock
    private FileCategoryDao fileCategoryDao;

    @Mock
    private FileDao fileDao;

    private FileCategoryService service;

    @BeforeEach
    void setUp() {
        service = new FileCategoryService();
        ReflectionTestUtils.setField(service, "fileCategoryDao", fileCategoryDao);
        ReflectionTestUtils.setField(service, "fileDao", fileDao);
    }

    private static FileCategoryEntity category(Long id, String code) {
        FileCategoryEntity entity = new FileCategoryEntity();
        entity.setCategoryId(id);
        entity.setCategoryCode(code);
        entity.setCategoryName(code);
        entity.setSort(1);
        return entity;
    }

    @Test
    @DisplayName("内置分类不许删 —— 代码按 code 引用它们，删了历史文件成孤儿且代码直接抛异常")
    void systemCategoryCannotBeDeleted() {
        when(fileCategoryDao.selectById(2L)).thenReturn(category(2L, "NOTICE"));

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置分类不允许删除");
    }

    @Test
    @DisplayName("内置分类的 code 不许改，但名称随便改 —— 改名不影响任何引用")
    void systemCategoryCodeIsImmutableButNameIsNot() {
        when(fileCategoryDao.selectById(2L)).thenReturn(category(2L, "NOTICE"));

        FileCategoryEntity renameCode = category(2L, "ANNOUNCE");
        assertThatThrownBy(() -> service.update(renameCode, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编码不允许修改");

        FileCategoryEntity renameName = category(2L, "NOTICE");
        renameName.setCategoryName("通知公告");
        service.update(renameName, null);
        verify(fileCategoryDao).updateById(any(FileCategoryEntity.class));
    }

    @Test
    @DisplayName("分类下还有文件不许删 —— 否则那些文件的 category_id 指向不存在的行，成为幽灵")
    void categoryWithFilesCannotBeDeleted() {
        when(fileCategoryDao.selectById(9L)).thenReturn(category(9L, "BANNER"));
        when(fileDao.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还有 3 个文件");
    }

    @Test
    @DisplayName("空分类可以删")
    void emptyCategoryCanBeDeleted() {
        when(fileCategoryDao.selectById(9L)).thenReturn(category(9L, "BANNER"));
        when(fileDao.selectCount(any())).thenReturn(0L);

        service.delete(9L);
        verify(fileCategoryDao).deleteById(9L);
    }

    @Test
    @DisplayName("code 字符集必须收窄 —— 它会成为 storageKey 的第一段前缀")
    void codeCharsetIsRestricted() {
        for (String bad : new String[]{"活动素材", "a/b", "a b", "", "a".repeat(51)}) {
            FileCategoryEntity form = category(null, bad);
            assertThatThrownBy(() -> service.add(form, null))
                    .as(bad)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("code 重复被拒")
    void duplicateCodeRejected() {
        when(fileCategoryDao.getByCode("BANNER")).thenReturn(category(9L, "BANNER"));

        assertThatThrownBy(() -> service.add(category(null, "BANNER"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("拖拽排序按给定顺序重写 sort，从 1 开始连续")
    void reorderRewritesSortSequentially() {
        service.reorder(List.of(30L, 10L, 20L), null);

        ArgumentCaptor<FileCategoryEntity> captor = ArgumentCaptor.forClass(FileCategoryEntity.class);
        verify(fileCategoryDao, times(3)).updateById(captor.capture());

        List<FileCategoryEntity> updates = captor.getAllValues();
        assertThat(updates).extracting(FileCategoryEntity::getCategoryId)
                .containsExactly(30L, 10L, 20L);
        assertThat(updates).extracting(FileCategoryEntity::getSort)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("空排序请求直接返回，不发无意义的 UPDATE")
    void reorderIgnoresEmpty() {
        service.reorder(null, null);
        service.reorder(List.of(), null);
        verify(fileCategoryDao, times(0)).updateById(any(FileCategoryEntity.class));
    }
}
