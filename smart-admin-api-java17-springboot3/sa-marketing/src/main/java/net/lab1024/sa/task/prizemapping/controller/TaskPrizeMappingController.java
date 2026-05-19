package net.lab1024.sa.task.prizemapping.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingAddForm;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingUpdateForm;
import net.lab1024.sa.task.prizemapping.domain.vo.TaskPrizeMappingVO;
import net.lab1024.sa.task.prizemapping.service.TaskPrizeMappingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务阶段与奖励映射表")
@RequestMapping("/taskPrizeMapping")
public class TaskPrizeMappingController {

    private final TaskPrizeMappingService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<TaskPrizeMappingVO>> queryPage(@RequestBody @Valid TaskPrizeMappingQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid TaskPrizeMappingAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskPrizeMappingUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
