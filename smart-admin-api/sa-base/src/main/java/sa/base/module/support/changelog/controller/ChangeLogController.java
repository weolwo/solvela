package sa.base.module.support.changelog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import sa.base.common.controller.SupportBaseController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.constant.SwaggerTagConst;
import sa.base.module.support.changelog.domain.form.ChangeLogQueryForm;
import sa.base.module.support.changelog.domain.vo.ChangeLogVO;
import sa.base.module.support.changelog.service.ChangeLogService;
import org.springframework.web.bind.annotation.*;

/**
 * 系统更新日志 Controller
 *
 * @Author 卓大
 * @Date 2022-09-26 14:53:50
 * @Copyright 1024创新实验室
 */

@RestController
@Tag(name = SwaggerTagConst.Support.CHANGE_LOG)
public class ChangeLogController extends SupportBaseController {

    @Resource
    private ChangeLogService changeLogService;

    @Operation(summary = "分页查询 @author 卓大")
    @PostMapping("/changeLog/queryPage")
    public ResponseDTO<PageResult<ChangeLogVO>> queryPage(@RequestBody @Valid ChangeLogQueryForm queryForm) {
        return ResponseDTO.ok(changeLogService.queryPage(queryForm));
    }


    @Operation(summary = "变更内容详情 @author 卓大")
    @GetMapping("/changeLog/getDetail/{changeLogId}")
    public ResponseDTO<ChangeLogVO> getDetail(@PathVariable Long changeLogId) {
        return ResponseDTO.ok(changeLogService.getById(changeLogId));
    }
}