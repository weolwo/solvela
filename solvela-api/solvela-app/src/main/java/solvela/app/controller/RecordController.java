package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.RecordView;
import solvela.app.service.RecordService;

import java.util.List;

/**
 * 我的记录（奖励发放记录）。
 *
 * <h3>没有 @Anonymous：这是「我的」东西</h3>
 * 每一条都带着这个会员中了什么、发没发到，没有登录态就没有内容。
 *
 * <h3>会员号从登录态取，接口上没有 memberId 参数</h3>
 * 有了它就等于开放「查任意会员的中奖记录」。与资产那条链路同一个规矩。
 */
@Tag(name = "我的记录")
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    /**
     * 我最近的奖励记录，按时间倒序。没有记录时返回<b>空数组</b>，不是 404。
     *
     * <p>只给最近若干条 —— 「全部记录」是另一页的事，那时才需要分页，
     * 而分页形状由那一页的需求定（要不要跳页），现在不预先猜。
     */
    @GetMapping
    public List<RecordView> listRecent() {
        return recordService.listRecent(CurrentMember.require().memberId());
    }
}
