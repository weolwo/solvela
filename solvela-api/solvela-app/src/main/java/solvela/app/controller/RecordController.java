package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.RecordView;
import solvela.app.service.RecordService;

import java.util.List;

/**
 * 我的记录。<b>三种记录三条路径，不合并成一个「全部」列表。</b>
 *
 * <p>它们的状态机、金额口径、该显示什么完全不同，合并之后每一条
 * 都只能显示最小公约数。而用户点进来时心里想的本来就是具体的一件事：
 * 「我兑的东西呢」或者「我中的奖呢」。
 *
 * <p>兑换记录不在这里 —— 它属于商城，在 {@code /mall/order}。
 */
@Tag(name = "我的记录")
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    /**
     * 奖励记录：我在活动里中过什么。
     *
     * @param activityCode 只看这一个活动的，<b>不传表示全部</b>。
     *                     活动专题页传它，「我的」页不传
     */
    @GetMapping("/prize")
    public List<RecordView> listPrizeRecords(@RequestParam(required = false) String activityCode) {
        return recordService.listPrizeRecords(CurrentMember.require().memberId(), activityCode);
    }

    /**
     * 优惠记录：平台要发给我什么，包括还在路上的。
     *
     * <p>底层是提案记录。<b>「提案」是运营视角的词，不出现在 C 端任何地方</b> ——
     * 用户不需要知道他的奖励要过两道审批。
     */
    @GetMapping("/promo")
    public List<RecordView> listPromoRecords() {
        return recordService.listPromoRecords(CurrentMember.require().memberId());
    }
}
