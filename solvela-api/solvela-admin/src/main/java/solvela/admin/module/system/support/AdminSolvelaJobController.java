package solvela.admin.module.system.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import solvela.admin.module.system.support.SupportBaseController;
import solvela.base.domain.PageResult;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.web.ResponseDTO;
import solvela.admin.auth.CurrentEmployee;
import solvela.base.constant.SwaggerTagConst;
import solvela.admin.module.system.job.api.SolvelaJobService;
import solvela.admin.module.system.job.api.domain.*;
import solvela.admin.module.system.job.config.SolvelaJobAutoConfiguration;
import solvela.admin.module.system.repeatsubmit.annotation.RepeatSubmit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务 管理接口
 *
 * @author huke
 * @date 2024/6/17 20:41
 */
@Tag(name = SwaggerTagConst.Support.JOB)
@RestController
@ConditionalOnBean(SolvelaJobAutoConfiguration.class)
// 🔴 与 SolvelaJobService 用同一个角色条件：只有 ADMIN / ALL 才暴露管理接口。
//    不加这一条，WORKER 节点也会暴露一整套 job 管理 API（且注入不到 Service 直接启动失败）
@ConditionalOnExpression(
        "'${solvela.job.role:ALL}'.equalsIgnoreCase('ADMIN') or '${solvela.job.role:ALL}'.equalsIgnoreCase('ALL')")
public class AdminSolvelaJobController extends SupportBaseController {

    @Autowired
    private SolvelaJobService jobService;

    @Operation(summary = "定时任务-立即执行 @huke")
    @PostMapping("/job/execute")
    @RepeatSubmit
    public ResponseDTO<String> execute(@RequestBody @Valid SolvelaJobExecuteForm executeForm) {
        RequestEmployee requestUser = CurrentEmployee.orNull();
        executeForm.setUpdateName(requestUser.getUserName());
        return jobService.execute(executeForm);
    }

    @Operation(summary = "定时任务-查询详情 @huke")
    @GetMapping("/job/{jobId}")
    public ResponseDTO<SolvelaJobVO> queryJobInfo(@PathVariable Integer jobId) {
        return jobService.queryJobInfo(jobId);
    }

    @Operation(summary = "定时任务-分页查询 @huke")
    @PostMapping("/job/query")
    public ResponseDTO<PageResult<SolvelaJobVO>> queryJob(@RequestBody @Valid SolvelaJobQueryForm queryForm) {
        return jobService.queryJob(queryForm);
    }

    @Operation(summary = "定时任务-添加任务 @huke")
    @PostMapping("/job/add")
    @RepeatSubmit
    public ResponseDTO<String> addJob(@RequestBody @Valid SolvelaJobAddForm addForm) {
        RequestEmployee requestUser = CurrentEmployee.orNull();
        addForm.setUpdateName(requestUser.getUserName());
        return jobService.addJob(addForm);
    }

    @Operation(summary = "定时任务-更新-任务信息 @huke")
    @PostMapping("/job/update")
    @RepeatSubmit
    public ResponseDTO<String> updateJob(@RequestBody @Valid SolvelaJobUpdateForm updateForm) {
        RequestEmployee requestUser = CurrentEmployee.orNull();
        updateForm.setUpdateName(requestUser.getUserName());
        return jobService.updateJob(updateForm);
    }

    @Operation(summary = "定时任务-更新-开启状态 @huke")
    @PostMapping("/job/update/enabled")
    @RepeatSubmit
    public ResponseDTO<String> updateJobEnabled(@RequestBody @Valid SolvelaJobEnabledUpdateForm updateForm) {
        RequestEmployee requestUser = CurrentEmployee.orNull();
        updateForm.setUpdateName(requestUser.getUserName());
        return jobService.updateJobEnabled(updateForm);
    }

    @Operation(summary = "定时任务-删除 @zhuoda")
    @GetMapping("/job/delete")
    @RepeatSubmit
    public ResponseDTO<String> deleteJob(@RequestParam Integer jobId) {
        return jobService.deleteJob(jobId, CurrentEmployee.orNull());
    }

    @Operation(summary = "定时任务-执行记录-分页查询 @huke")
    @PostMapping("/job/log/query")
    public ResponseDTO<PageResult<SolvelaJobLogVO>> queryJobLog(@RequestBody @Valid SolvelaJobLogQueryForm queryForm) {
        return jobService.queryJobLog(queryForm);
    }

    @Operation(summary = "定时任务-查询所有已注册的执行器（新增任务时下拉选择） @alaric")
    @GetMapping("/job/handler/list")
    public ResponseDTO<List<SolvelaJobHandlerVO>> queryHandlerList() {
        return jobService.queryHandlerList();
    }

    /**
     * 🔴 用<b>当初那一次</b>的参数与业务日期重跑，不是拿当前配置跑一遍。
     *
     * <p>任务失败后运营的第一反应就是重跑 —— 没有这个入口，
     * 所有告警都会退化成「通知开发去查」。
     */
    @Operation(summary = "定时任务-一键重跑（沿用原参数与业务日期） @alaric")
    @GetMapping("/job/log/rerun")
    @RepeatSubmit
    public ResponseDTO<String> rerun(@RequestParam Long logId) {
        return jobService.rerun(logId, CurrentEmployee.nameOrNull());
    }

    /**
     * ⚠️ 返回的是「已发出中断信号」而不是「已终止」——
     * 能否真正停止取决于该执行器是否响应中断，谎报成功比不提供这个功能更糟。
     */
    @Operation(summary = "定时任务-终止正在执行的任务（发出中断信号） @alaric")
    @GetMapping("/job/log/terminate")
    @RepeatSubmit
    public ResponseDTO<String> terminate(@RequestParam Long logId) {
        return jobService.terminate(logId, CurrentEmployee.nameOrNull());
    }

    /**
     * ⚠️ 新建任务时预览是<b>不精确</b>的（`exact=false`）：打散偏移由 jobId 决定，
     * 而 id 要等保存后才有。前端必须把这一点如实呈现给运营，不要只显示时刻。
     */
    @Operation(summary = "定时任务-触发时间预览（保存前算出接下来5次） @alaric")
    @PostMapping("/job/trigger/preview")
    public ResponseDTO<SolvelaJobTriggerPreviewVO> previewTriggerTime(
            @RequestBody @Valid SolvelaJobTriggerPreviewForm form) {
        return jobService.previewTriggerTime(form);
    }

    @Operation(summary = "定时任务-查看某次执行的实时日志 @alaric")
    @GetMapping("/job/log/detail")
    public ResponseDTO<List<String>> queryExecuteLog(@RequestParam Long logId) {
        return jobService.queryExecuteLog(logId);
    }
}