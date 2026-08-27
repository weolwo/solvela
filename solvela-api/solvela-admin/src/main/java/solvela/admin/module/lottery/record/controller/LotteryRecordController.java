package solvela.admin.module.lottery.record.controller;

import solvela.admin.module.lottery.record.domain.form.LotteryRecordQueryForm;
import solvela.lottery.record.domain.query.LotteryRecordQuery;
import solvela.lottery.record.domain.dto.LotteryRecordFunnelDTO;
import solvela.admin.module.lottery.record.domain.vo.LotteryRecordVO;
import solvela.lottery.record.domain.dto.LotteryRecordDTO;
import solvela.lottery.record.service.LotteryRecordService;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 用户号码记录 Controller —— <b>只读</b>。
 *
 * <h3>⚠️ add / update 已刻意移除，不要再加回来</h3>
 * 这张表存的是<b>用户手里的号码本身</b>，比抽奖流水更不能碰：
 * <ul>
 *   <li>{@code security_sign} 是防篡改签名，用户凭它自证「这个号码确实是系统发给我的」。
 *       后台能改签名，整套自证机制就是摆设；</li>
 *   <li>{@code win_status} / {@code prize_level} / {@code prize_code} 是派奖依据，
 *       改一行等于凭空造一个中奖者，或抹掉一个真中奖者；</li>
 *   <li>{@code ticket_number} 与 {@code sequence_no} 是 FPE 双射的两端，
 *       改任一个都会让号码反解验真失败。</li>
 * </ul>
 * 而且这两个接口从来没有正当用途：记录由 {@code TicketPersistService} 在领号链路里写入、
 * 由开奖核销 SQL 批量更新中奖状态，没有任何场景需要人工补录或修改一张号码。
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "用户号码记录")
@RequestMapping("/lotteryRecord")
public class LotteryRecordController {

    private final LotteryRecordService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("lotteryRecord:query")
    public ResponseDTO<PageResult<LotteryRecordVO>> queryPage(@RequestBody @Valid LotteryRecordQueryForm queryForm) {
        PageResult<LotteryRecordDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, LotteryRecordQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, LotteryRecordVO.class));
    }

    @Operation(summary = "购彩漏斗：中奖率、奖级分布、派发状态与数据一致性体检")
    @PostMapping("/funnel")
    @SaCheckPermission("lotteryRecord:query")
    public ResponseDTO<LotteryRecordFunnelDTO> funnel(@RequestBody @Valid LotteryRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.funnel(SolvelaBeanUtil.copy(queryForm, LotteryRecordQuery.class)));
    }

}
