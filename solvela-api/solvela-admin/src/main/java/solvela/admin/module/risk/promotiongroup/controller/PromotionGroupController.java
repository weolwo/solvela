package solvela.admin.module.risk.promotiongroup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.risk.promotiongroup.domain.form.PromotionGroupCopyForm;
import solvela.admin.module.risk.promotiongroup.domain.form.PromotionGroupItemStatusForm;
import solvela.admin.module.risk.promotiongroup.domain.form.PromotionGroupQueryForm;
import solvela.admin.module.risk.promotiongroup.domain.form.PromotionGroupStatusForm;
import solvela.admin.module.risk.promotiongroup.domain.form.PromotionGroupWorkbenchSaveForm;
import solvela.admin.module.risk.promotiongroup.domain.vo.PromotionGroupVO;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.risk.promotiongroup.domain.command.PromotionGroupWorkbenchSaveCommand;
import solvela.risk.promotiongroup.domain.dto.PromotionGroupDTO;
import solvela.risk.promotiongroup.domain.dto.PromotionGroupWorkbenchDTO;
import solvela.risk.promotiongroup.domain.query.PromotionGroupQuery;
import solvela.risk.promotiongroup.service.PromotionGroupService;
import solvela.web.RequiresPermission;

/**
 * 优惠配置分组 Controller
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "优惠配置分组")
@RequestMapping("/promotionGroup")
public class PromotionGroupController {

    private final PromotionGroupService promotionGroupService;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("promotionGroup:query")
    public PageResult<PromotionGroupVO> queryPage(@RequestBody @Valid PromotionGroupQueryForm queryForm) {
        PageResult<PromotionGroupDTO> page =
                promotionGroupService.queryPage(SolvelaBeanUtil.copy(queryForm, PromotionGroupQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, PromotionGroupVO.class);
    }

    /**
     * 工作台聚合回显。id 不传即「新建」，返回一个带默认值的空壳 ——
     * 前端不必为新建与编辑写两套初始化逻辑。
     */
    @Operation(summary = "工作台聚合回显")
    @GetMapping("/workbenchDetail")
    @RequiresPermission("promotionGroup:query")
    public PromotionGroupWorkbenchDTO workbenchDetail(@RequestParam(required = false) Long id) {
        return promotionGroupService.workbenchDetail(id);
    }

    /**
     * 工作台聚合保存。返回分组 ID —— 新建保存后前端要用它把页面切到编辑态，
     * 否则再点一次保存会又建一个新组。
     */
    @Operation(summary = "工作台聚合保存")
    @PostMapping("/workbenchSave")
    @RequiresPermission("promotionGroup:update")
    public Long workbenchSave(@RequestBody @Valid PromotionGroupWorkbenchSaveForm form) {
        // 🔴 必须 deepCopy，不能用 copy：本表单含嵌套集合（itemList）。
        // BeanUtils.copyProperties 会解析泛型，发现 List<PromotionGroupItemForm> 与
        // List<PromotionGroupItemCommand> 不兼容后【直接跳过该属性】—— 既不报错也不转换，
        // Command.itemList 留在 null。表现极其隐蔽：接口返回 200、分组也建出来了、
        // 页面弹「保存成功」，只是那几条优惠配置一条都没存，
        // 而且编辑既有分组时还会把组内已有的类型全部当成「本次未提交」给停用掉。
        // 房子里所有含嵌套集合的工作台保存都用 deepCopy（彩票、抽奖、商城、任务），
        // SolvelaBeanUtil.deepCopy 的注释与 SolvelaBeanUtilCopyTest 把这条钉死了。
        return promotionGroupService.workbenchSave(
                SolvelaBeanUtil.deepCopy(form, PromotionGroupWorkbenchSaveCommand.class));
    }

    @Operation(summary = "分组主开关：停用会连带停用组内全部配置；启用需指定要开哪几种类型")
    @PostMapping("/updateStatus")
    @RequiresPermission("promotionGroup:update")
    public void updateStatus(@RequestBody @Valid PromotionGroupStatusForm form) {
        promotionGroupService.updateStatus(form.getId(), form.getStatus(), form.getEnablePrizeTypes());
    }

    @Operation(summary = "复制分组：连同组内每种类型的配置一起复制成新的一份（水位归零）")
    @PostMapping("/copy")
    @RequiresPermission("promotionGroup:update")
    public Long copy(@RequestBody @Valid PromotionGroupCopyForm form) {
        return promotionGroupService.copy(form.getId(), form.getGroupName());
    }

    @Operation(summary = "组内单条配置的发放开关：供分组列表展开行直接拨")
    @PostMapping("/updateItemStatus")
    @RequiresPermission("promotionGroup:update")
    public void updateItemStatus(@RequestBody @Valid PromotionGroupItemStatusForm form) {
        promotionGroupService.updateItemStatus(form.getId(), form.getStatus());
    }

    @Operation(summary = "解散分组：组删掉，组内配置保留并变回未分组")
    @GetMapping("/delete/{id}")
    @RequiresPermission("promotionGroup:delete")
    public void delete(@PathVariable Long id) {
        promotionGroupService.delete(id);
    }
}
