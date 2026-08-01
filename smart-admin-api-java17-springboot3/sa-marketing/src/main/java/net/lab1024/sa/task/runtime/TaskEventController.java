package net.lab1024.sa.task.runtime;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.task.recordflow.dao.TaskRecordFlowDao;
import net.lab1024.sa.task.recordflow.domain.entity.TaskRecordFlow;
import net.lab1024.sa.task.runtime.domain.TaskEventReportForm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务事件 Controller（运行态入口）。
 *
 * <p>⚠️ <b>权限串写全，不要复制生成器产物的那一份</b>：
 * 活动域与奖品域的 Controller 都曾写成 {@code :addProposal} 和不带前缀的 {@code :query}，
 * 而库里 menu 的功能点串一直是对的 —— 两边永远对不上，
 * 表现是「任何受限角色即便被正确授权也依然被拒」，而超管因为 {@code administratorFlag}
 * 绕过全部校验，所以长期没人发现（交接文档 §4.5 / §4.6④）。
 *
 * @author alaric
 * @date 2026-08-01
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "任务事件（运行态）")
@RequestMapping("/taskEvent")
public class TaskEventController {

    private final TaskEventService taskEventService;
    private final TaskRecordFlowDao taskRecordFlowDao;

    /**
     * 上报任务事件。立即返回，处理在任务事件线程池里异步进行。
     *
     * <p>返回失败只有一种情况：线程池队列打满（拒绝策略 AbortPolicy）——
     * 这是给上游的重试信号，别忽略它。
     */
    @Operation(summary = "上报任务事件（异步推进进度，达标自动发奖）")
    @PostMapping("/report")
    @SaCheckPermission("taskEvent:report")
    public ResponseDTO<String> report(@RequestBody @Valid TaskEventReportForm form) {
        return taskEventService.report(form);
    }

    /**
     * 查一条任务记录的事件流水 —— <b>客诉自证的入口</b>。
     *
     * <p>「用户下了 99 元的单为什么没进度」这类问题，答案就在 {@code discard_reason} 里。
     * 一个查不清账的中台，用几次就没人敢用了。
     */
    @Operation(summary = "查询任务记录的事件流水（含被丢弃的事件与原因）")
    @GetMapping("/flow/{recordId}")
    @SaCheckPermission("taskRecord:query")
    public ResponseDTO<List<TaskRecordFlow>> queryFlow(@PathVariable Long recordId) {
        return ResponseDTO.ok(taskRecordFlowDao.selectByRecordId(recordId));
    }
}
