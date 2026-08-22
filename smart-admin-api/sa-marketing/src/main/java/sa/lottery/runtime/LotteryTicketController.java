package sa.lottery.runtime;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.ResponseDTO;
import sa.lottery.record.domain.entity.LotteryRecord;
import sa.lottery.runtime.domain.TicketObtainForm;
import sa.lottery.runtime.domain.TicketObtainVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 彩票领号与查询（运行态）。
 *
 * 调用方是上游业务服务，不是浏览器 —— 消耗与限购已在上游完成，本模块只发号。
 * 后续这两个能力会通过 {@code @ScriptFunction} 挂到 QLExpress 上供规则脚本直接调用，
 * 届时 Service 的方法可以直接复用，不需要再包一层。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "彩票领号")
@RequestMapping("/lotteryTicket")
public class LotteryTicketController {

    private final TicketIssueService ticketIssueService;
    private final TicketQueryService ticketQueryService;

    @Operation(summary = "领号：为用户发一个彩票号码")
    @PostMapping("/obtain")
    @SaCheckPermission("lotteryTicket:query")
    public ResponseDTO<TicketObtainVO> obtain(@RequestBody @Valid TicketObtainForm form) {
        return ticketIssueService.obtain(form);
    }

    @Operation(summary = "我的号码：按奖级升序，未中奖(99)沉底")
    @GetMapping("/myTickets")
    @SaCheckPermission("lotteryTicket:query")
    public ResponseDTO<List<LotteryRecord>> myTickets(@RequestParam String lotteryCode,
                                                      @RequestParam(required = false) String issueNo,
                                                      @RequestParam Long memberId) {
        return ticketQueryService.myTickets(lotteryCode, issueNo, memberId);
    }

    @Operation(summary = "号码验真：反解游标 + 校验签名，供客服核对用户出示的号码")
    @GetMapping("/verify")
    @SaCheckPermission("lotteryTicket:query")
    public ResponseDTO<String> verify(@RequestParam String lotteryCode,
                                      @RequestParam String issueNo,
                                      @RequestParam String ticketNumber) {
        return ticketQueryService.verify(lotteryCode, issueNo, ticketNumber);
    }
}
