package solvela.mall.category.service;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.base.module.file.service.FileAssetService;
import solvela.mall.MallCategory;
import solvela.mall.category.dao.MallCategoryDao;
import solvela.mall.category.domain.command.MallCategorySaveCommand;
import solvela.mall.category.manager.MallCategoryManager;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.constant.MallConst;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类图标的<b>引用登记</b>与<b>清空</b>。
 *
 * <h3>🔴 这两条都是 2026-09-25 真机验证时撞出来的</h3>
 * 在此之前分类图标从来没有登记过引用：前端有上传口，后端只把 fileId 存进
 * {@code icon_file_id} 就完事了。孤儿清理任务一上线，C 端宫格导航的图标会集体变叉。
 *
 * <p>补上 {@code confirm} 之后，测「移除图标」时又撞出第二个：引用清掉了，
 * 而 {@code icon_file_id} 纹丝不动 —— MyBatis-Plus 的 NOT_NULL 更新策略把 null 字段
 * 整个排除在 SET 之外。库里于是出现「分类指着 147、而 147 没有任何人引用」这种自相矛盾，
 * <b>而清理任务会照着后半句去删文件</b>。
 *
 * @author alaric
 * @date 2026-09-25
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallCategoryIconRelationTest {

    @Mock
    private MallCategoryDao mallCategoryDao;

    @Mock
    private MallCategoryManager mallCategoryManager;

    @Mock
    private MallCommodityManager mallCommodityManager;

    @Mock
    private FileAssetService fileAssetService;

    @InjectMocks
    private MallCategoryService service;

    private static MallCategorySaveCommand form(Long id, Long iconFileId) {
        MallCategorySaveCommand command = new MallCategorySaveCommand();
        command.setId(id);
        command.setParentId(0L);
        command.setCategoryName("美食");
        command.setIconFileId(iconFileId);
        return command;
    }

    private static MallCategory existing(Long id, Long iconFileId) {
        MallCategory entity = new MallCategory();
        entity.setId(id);
        entity.setParentId(0L);
        entity.setCategoryName("美食");
        entity.setIconFileId(iconFileId);
        return entity;
    }

    /** 名称查重那条走 manager 的 lambdaQuery，这里让它返回空集合就够了 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubNameCheckPasses() {
        var query = mock(com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(mallCategoryManager.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.list()).thenReturn(List.of());
    }

    @Test
    @DisplayName("🔴 保存分类时要登记图标引用 —— 不登记的话孤儿清理会把它当垃圾删掉")
    void saveRegistersIconReference() {
        stubNameCheckPasses();
        when(mallCategoryManager.getById(6L)).thenReturn(existing(6L, null));

        service.save(form(6L, 147L), "tester");

        verify(fileAssetService).confirm(List.of(147L), MallConst.BIZ_TYPE_CATEGORY, 6L);
    }

    @Test
    @DisplayName("🔴 移除图标：引用要清掉，icon_file_id 也要真的变成 null")
    void removingIconClearsBothRelationAndColumn() {
        // 只做了前半句的话，库里就是「分类指着 147、而 147 没人引用」——
        // 清理任务照着后半句删文件，分类那个格子从此是个叉
        stubNameCheckPasses();
        when(mallCategoryManager.getById(6L)).thenReturn(existing(6L, 147L));
        LambdaUpdateChainWrapper<MallCategory> update = mock(LambdaUpdateChainWrapper.class);
        when(mallCategoryManager.lambdaUpdate()).thenReturn(update);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.set(any(), any())).thenReturn(update);

        service.save(form(6L, null), "tester");

        // 空集合而不是跳过：它表示「这个分类现在一张图都不引用了」
        verify(fileAssetService).confirm(List.of(), MallConst.BIZ_TYPE_CATEGORY, 6L);
        // 而且必须补那条显式清列的 UPDATE，updateById 是清不掉 null 字段的
        verify(update).update();
    }

    @Test
    @DisplayName("图标没变时不该多发一条清列的 UPDATE")
    void keepingIconDoesNotIssueClearUpdate() {
        stubNameCheckPasses();
        when(mallCategoryManager.getById(6L)).thenReturn(existing(6L, 147L));

        service.save(form(6L, 147L), "tester");

        verify(mallCategoryManager, never()).lambdaUpdate();
        verify(fileAssetService).confirm(eq(List.of(147L)), eq(MallConst.BIZ_TYPE_CATEGORY), eq(6L));
    }
}
