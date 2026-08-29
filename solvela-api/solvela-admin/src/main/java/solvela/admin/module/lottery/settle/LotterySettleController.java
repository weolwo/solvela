package solvela.admin.module.lottery.settle;

import solvela.lottery.settle.LotterySettleService;
import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import solvela.web.ResponseDTO;
import solvela.lottery.settle.domain.SettleResultDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开奖核销与派奖
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "彩票开奖")
@RequestMapping("/lotteryIssue")
public class LotterySettleController {

    private final LotterySettleService lotterySettleService;

    @Operation(summary = "服务端摇号：SecureRandom 生成，前端不可信")
    @GetMapping("/randomNumber")
    @RequiresPermission("lotteryIssue:query")
    public ResponseDTO<String> randomNumber(@RequestParam Long issueId) {
        return ResponseDTO.ok(lotterySettleService.randomNumber(issueId));
    }

    @Operation(summary = "执行开奖核销：状态闸门 + 按奖级升序逐级认领。核销中重复调用会接着跑，不会重复认领")
    @PostMapping("/settle")
    @RequiresPermission("lotteryIssue:update")
    public ResponseDTO<SettleResultDTO> settle(@RequestParam Long issueId,
                                              @RequestParam(required = false) String winningNumber) {
        return ResponseDTO.ok(lotterySettleService.settle(issueId, winningNumber));
    }

    @Operation(summary = "核销进度：中奖/未中奖/待派奖各多少")
    @GetMapping("/settleSummary")
    @RequiresPermission("lotteryIssue:query")
    public ResponseDTO<Map<String, Object>> settleSummary(@RequestParam Long issueId) {
        return ResponseDTO.ok(lotterySettleService.summary(issueId));
    }

    @Operation(summary = "触发派奖：把中奖记录分批投递进公共派发链路")
    @PostMapping("/dispatch")
    @RequiresPermission("lotteryIssue:update")
    public ResponseDTO<Integer> dispatch(@RequestParam Long issueId) {
        return ResponseDTO.ok(lotterySettleService.dispatch(issueId));
    }
}
