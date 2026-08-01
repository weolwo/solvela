package net.lab1024.sa.risk.proposal.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordAddForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordUpdateForm;
import net.lab1024.sa.risk.proposal.domain.vo.ProposalRecordVO;
import net.lab1024.sa.risk.proposal.service.ProposalRecordService;
import org.springframework.web.bind.annotation.*;

/**
 * 提案表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "提案表")
@RequestMapping("/proposalRecord")
public class ProposalRecordController {

    private final ProposalRecordService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("proposalRecord:query")
    public ResponseDTO<PageResult<ProposalRecordVO>> queryPage(@RequestBody @Valid ProposalRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "提案审批通过（财务视角；一审通过后按 review_level 决定进二审还是直接放行下发）")
    @GetMapping("/approve/{id}")
    @SaCheckPermission("proposalRecord:approve")
    public ResponseDTO<String> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return Service.approve(id, SmartRequestUtil.getRequestUser().getUserName(), comment);
    }

    @Operation(summary = "提案审批驳回")
    @GetMapping("/reject/{id}")
    @SaCheckPermission("proposalRecord:approve")
    public ResponseDTO<String> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return Service.reject(id, SmartRequestUtil.getRequestUser().getUserName(), comment);
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("proposalRecord:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProposalRecordAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("proposalRecord:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProposalRecordUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("proposalRecord:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("proposalRecord:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
