package net.lab1024.sa.task.prizemapping.controller;

import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
import net.lab1024.sa.task.prizemapping.domain.vo.TaskPrizeMappingVO;
import net.lab1024.sa.task.prizemapping.service.TaskPrizeMappingService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 任务阶段与奖励映射表 Controller
 *
 * <p>🔴 <b>只读，故意不提供增删改。</b> 这张表没有独立生命周期，它由任务向导整体托管：
 * {@code TaskConfigService.wizardUpdate} 每次保存都是「按 task_config_id 全删 + insertBatch 重插」。
 * 所以在这里单条新增/修改，只要运营下次在向导里点一次保存就被无声抹掉 ——
 * 一个改了不生效、还不报错的入口比没有入口更糟。
 *
 * <p>删除同理不给：它确实能让 {@link net.lab1024.sa.task.runtime.TaskPrizeDispatcher} 发不出奖，
 * 但那是<b>物理删除、不可逆</b>的停发方式，而且删中间档位会把跑着的活动改坏
 * （已拿过第 1 档的用户下次直接跨到第 3 档，progress_data.dispatchedStages 与实际对不上）。
 * 停发的正确做法是把任务下线（{@code taskConfig:update}）—— 可逆、有状态可查、运行态不再订阅事件。
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务阶段与奖励映射表")
@RequestMapping("/taskPrizeMapping")
public class TaskPrizeMappingController {

    private final TaskPrizeMappingService taskPrizeMappingService;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("taskPrizeMapping:query")
    public ResponseDTO<PageResult<TaskPrizeMappingVO>> queryPage(@RequestBody @Valid TaskPrizeMappingQueryForm queryForm) {
        return ResponseDTO.ok(taskPrizeMappingService.queryPage(queryForm));
    }
}
