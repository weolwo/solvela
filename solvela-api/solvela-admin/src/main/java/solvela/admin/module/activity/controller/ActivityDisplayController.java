package solvela.admin.module.activity.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import solvela.activity.ActivityDisplay;
import solvela.activity.service.ActivityDisplayService;
import solvela.admin.auth.CurrentEmployee;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动 C 端展示配置 Controller。
 *
 * <p><b>刻意与 {@code ActivityConfigController} 分开、走独立的保存事务</b>：
 * {@code save} 按 activityId 做 upsert，不关心活动是新建还是编辑。带来三个结果 ——
 * <ul>
 *   <li>{@code ActivityConfigService} 一行不用改，活动主表与展示配置互不牵连</li>
 *   <li>创建向导的最后一步、和活动编辑页，共用同一个组件与同一个接口</li>
 *   <li>步骤条怎么排（BASIC 少一步）是纯前端的事，后端不感知</li>
 * </ul>
 *
 * <p><b>展示配置应当可跳过</b>：所有字段都可空，运营常常在等设计出图，
 * 不该被这一步挡住「完成」。
 *
 * @Date 2026-08-10
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "活动配置")
@RequestMapping("/activityDisplay")
public class ActivityDisplayController {

    private final ActivityDisplayService activityDisplayService;

    /**
     * 按活动编码查。<b>对外一律用 activityCode 寻址</b> —— 整个活动域都是这么做的，
     * 而 {@code wizardCreate} 压根不返回 id，前端拿不到。
     */
    @Operation(summary = "查询活动展示配置 @author 1024")
    @GetMapping("/get/{activityCode}")
    @RequiresPermission("activityConfig:query")
    public ActivityDisplay get(@PathVariable String activityCode) {
        return activityDisplayService.getByActivityCode(activityCode);
    }

    /**
     * 保存（新增或更新由后端按 activityId 判断，前端不用区分）。
     *
     * <p>保存的同时会把引用到的全部图片登记进 {@code t_file_relation} ——
     * 包括三个独立图片字段和富文本正文里的图。不登记的话孤儿清理任务会把它们删掉。
     */
    @Operation(summary = "保存活动展示配置 @author 1024")
    @PostMapping("/save/{activityCode}")
    @RequiresPermission("activityConfig:update")
    public ActivityDisplay save(@PathVariable String activityCode,
                                             @RequestBody @Valid ActivityDisplay form) {
        return activityDisplayService.saveByCode(activityCode, form, CurrentEmployee.nameOrNull());
    }
}
