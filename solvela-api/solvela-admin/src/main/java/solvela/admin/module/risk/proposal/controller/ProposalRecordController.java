package solvela.admin.module.risk.proposal.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaRequestUtil;
import solvela.risk.proposal.domain.form.ProposalRecordQueryForm;
import solvela.risk.proposal.domain.vo.ProposalFunnelVO;
import solvela.risk.proposal.domain.vo.ProposalRecordVO;
import solvela.risk.proposal.service.ProposalRecordService;

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

    @Operation(summary = "提案漏斗：到账率、审批积压、下发卡单、资产/来源分布与流程体检")
    @PostMapping("/funnel")
    @SaCheckPermission("proposalRecord:query")
    public ResponseDTO<ProposalFunnelVO> funnel(@RequestBody @Valid ProposalRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.funnel(queryForm));
    }

    @Operation(summary = "提案审批通过（财务视角；一审通过后按 review_level 决定进二审还是直接放行下发）")
    @GetMapping("/approve/{id}")
    @SaCheckPermission("proposalRecord:approve")
    public ResponseDTO<String> approve(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return Service.approve(id, SolvelaRequestUtil.getRequestUser().getUserName(), comment);
    }

    @Operation(summary = "提案审批驳回")
    @GetMapping("/reject/{id}")
    @SaCheckPermission("proposalRecord:approve")
    public ResponseDTO<String> reject(@PathVariable Long id, @RequestParam(required = false) String comment) {
        return Service.reject(id, SolvelaRequestUtil.getRequestUser().getUserName(), comment);
    }

    /*
     * 生成器给的「添加 / 更新 / 批量删除 / 单个删除」四个接口已整组移除（v3.69.0）。
     *
     * 提案表是"钱出去的必经之路"，也是账务对账的主线：
     *   · 「新建」按钮会造出 status=0 的提案 —— 正常链路只落 10/30/80 三种初始状态，
     *     0 是绕过风控与预算直接插库的产物，不会被任何流程推进，也没人会发现；
     *     提案漏斗里那条"有 N 条提案停在等待中"的告警，来源就是这个按钮；
     *   · delete 是物理删除，而 t_physical_delivery.source_biz_id 和
     *     t_member_asset_transaction.biz_ref_id 都指着它 —— 删掉提案，
     *     下游的履约单和流水就成了无源之水，出账查不到依据。
     *
     * 提案只能由发奖链路创建（addProposal），由审批与下发推进状态。
     */
}
