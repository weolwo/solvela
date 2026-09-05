package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.marketing.api.PrizeRecordApi;
import solvela.marketing.api.PrizeRecordView;
import solvela.prize.prizelog.service.PrizeLogService;

import java.util.List;

/**
 * {@link PrizeRecordApi} 的 HTTP 薄壳。
 *
 * <p>照 {@link MemberAuthInternalController} 的形状：implements 接口，
 * 路径与方法只在契约里定义一次。控制器里不写业务，只做字段裁剪 ——
 * 实体上还有 {@code approveBy}（<b>审批人，运营账号</b>）、{@code proposalId}、
 * {@code remark} 等内部字段，一个都不该顺着这条链路流到 C 端。
 */
@RestController
@RequiredArgsConstructor
public class PrizeRecordInternalController implements PrizeRecordApi {

    private final PrizeLogService prizeLogService;

    @Override
    public List<PrizeRecordView> listRecentRecords(Long memberId, int limit) {
        return prizeLogService.listRecentByMember(memberId, limit).stream()
                .map(log -> new PrizeRecordView(
                        log.getId(),
                        log.getPrizeName(),
                        log.getPrizeType(),
                        log.getPrizeValue(),
                        log.getActivityCode(),
                        log.getActivityType(),
                        log.getStatus(),
                        log.getFailReason(),
                        log.getCreateTime()))
                .toList();
    }
}
