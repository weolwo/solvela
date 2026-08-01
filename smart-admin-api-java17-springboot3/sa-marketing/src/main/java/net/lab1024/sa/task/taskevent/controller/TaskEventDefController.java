package net.lab1024.sa.task.taskevent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.task.taskevent.domain.form.TaskEventAddForm;
import net.lab1024.sa.task.taskevent.domain.form.TaskEventQueryForm;
import net.lab1024.sa.task.taskevent.domain.form.TaskEventUpdateForm;
import net.lab1024.sa.task.taskevent.domain.vo.TaskEventOptionVO;
import net.lab1024.sa.task.taskevent.domain.vo.TaskEventVO;
import net.lab1024.sa.task.taskevent.service.TaskEventDefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务事件注册表 Controller（配置态）。
 *
 * <p>⚠️ 与 {@code net.lab1024.sa.task.runtime.TaskEventController}<b>共用 /taskEvent 前缀</b>，
 * 但子路径互不重叠：那边是运行态（{@code /report} 上报、{@code /flow/{id}} 流水查询），
 * 这边是配置态（注册表 CRUD 与下拉）。Spring 按完整路径匹配，两个 Controller 挂同一前缀是合法的。
 * 分成两个类是因为<b>配置态与运行态的读者和改动频率完全不同</b>，
 * 塞在一起会让人改注册表时误碰上报入口。
 *
 * <p>权限串写全前缀 —— 生成器产出的 Controller 曾整片写成 {@code ":query"} 这种缺前缀的串，
 * 与库里的功能点永远对不上，任何受限角色即便被正确授权也依然会被拒（见 v3.46.0.sql）。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务事件注册表")
@RequestMapping("/taskEvent")
public class TaskEventDefController {

    private final TaskEventDefService taskEventDefService;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("taskEventDef:query")
    public ResponseDTO<PageResult<TaskEventVO>> queryPage(@RequestBody @Valid TaskEventQueryForm queryForm) {
        return ResponseDTO.ok(taskEventDefService.queryPage(queryForm));
    }

    /**
     * 向导下拉。
     *
     * <p>只挂 {@code taskConfig:query}：能配任务的人就该能看到有哪些事件可选，
     * 不该再单独授一次 —— 否则「能进向导但下拉是空的」这种半残状态最难排查。
     */
    @Operation(summary = "事件下拉（向导第1步用，只返回启用中的）")
    @GetMapping("/optionList")
    @SaCheckPermission("taskConfig:query")
    public ResponseDTO<List<TaskEventOptionVO>> optionList() {
        return ResponseDTO.ok(taskEventDefService.optionList());
    }

    @Operation(summary = "注册新事件")
    @PostMapping("/add")
    @SaCheckPermission("taskEventDef:add")
    public ResponseDTO<String> add(@RequestBody @Valid TaskEventAddForm addForm) {
        return taskEventDefService.add(addForm);
    }

    @Operation(summary = "更新（事件编码不可改，UpdateForm 里刻意不定义该字段）")
    @PostMapping("/update")
    @SaCheckPermission("taskEventDef:update")
    public ResponseDTO<String> update(@RequestBody @Valid TaskEventUpdateForm updateForm) {
        return taskEventDefService.update(updateForm);
    }

    @Operation(summary = "删除（仍被任务配置引用时拒绝；建议优先用停用）")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("taskEventDef:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return taskEventDefService.delete(id);
    }
}
