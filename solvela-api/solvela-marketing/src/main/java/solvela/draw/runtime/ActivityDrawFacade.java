package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.marketing.api.DrawApi;
import solvela.marketing.api.DrawCmd;
import solvela.marketing.api.DrawResultView;
import solvela.draw.runtime.domain.DrawExecuteCommand;
import solvela.draw.runtime.domain.DrawExecuteResult;
import solvela.draw.runtime.domain.DrawRecord;

/**
 * 抽奖的<b>业务编排</b>入口。{@link DrawApi} 的实现。
 *
 * <h3>今天它只是转发，但它必须存在</h3>
 * {@link DrawExecuteService} 是纯粹的<b>命中判定与库存派发引擎</b>：抽一次消耗多少积分、
 * 要不要门票、无货时退不退，全部由上游算完再调进来（见它的类注释）。
 * 那个「上游」就是本类。
 *
 * <p>所以将来加消耗规则时，「扣积分 → 抽奖 → 发奖」那段编排长在<b>这里</b>，
 * 在一个本地事务里完成 —— 而不是长在网关里拼两次调用。
 * 拆成微服务后，网关拼两次写就是「用户扣了分没抽奖」，没有补偿路径。
 * 今天先把这个位置占住，比等到要加消耗时再找地方放便宜。
 *
 * <h3>顺带把引擎的 DTO 挡在契约之外</h3>
 * {@code DrawExecuteDTO} 是引擎自己的返回值，它可以随引擎演进；
 * {@code DrawResultView} 是对外承诺。中间这一层转换让两者各自变化，
 * 而不是引擎一改字段，所有调用方跟着重编译。
 *
 * @Date 2026-08-30
 */
@Service
@RequiredArgsConstructor
public class ActivityDrawFacade implements DrawApi {

    private final DrawExecuteService drawExecuteService;

    /**
     * <p>被拒（奖池未开启、重复提交、操作太频繁…）由引擎以 {@code reject} 返回，本方法原样透传 ——
     * <b>不在这里翻译成文案</b>：同一个 {@code POOL_CLOSED}，C 端要说「活动暂时休息一下」，
     * 而内部工具要看到的是原因本身。措辞是网关的决定。
     */
    @Override
    public DrawResultView draw(DrawCmd cmd) {
        DrawExecuteResult result = drawExecuteService.execute(
                DrawExecuteCommand.once(cmd.activityCode(), cmd.poolCode(), cmd.memberId(), cmd.requestId()));

        // 引擎与契约共用 DrawRejectReason，所以这里没有映射表 —— 映射表是会漂的：
        // 加一个原因忘了加映射，表现是返回一个 null 原因，而编译不报错
        return switch (result) {
            case DrawExecuteResult.Rejected(var reason) -> DrawResultView.ofReject(reason);
            case DrawExecuteResult.Executed(var records) -> toView(records.getFirst());
        };
    }

    /**
     * 单次结果 -> 对外契约。
     *
     * <p>⚠️ 这里补的两句文案（「恭喜中奖」「手慢了，奖品已被抽完」）是<b>过渡</b>：
     * 域已经不再返回 message（措辞该由调用方定，见 {@code DrawRecord} 的类注释），
     * 但契约 {@code DrawResultView} 目前还带着这个字段，网关也还在原样透传。
     * 等契约改成列表形态时，这两句应当搬到网关，本方法随之简化。
     */
    private static DrawResultView toView(DrawRecord record) {
        return record.hit()
                ? DrawResultView.ofHit(record.prizeItemId(), record.prizeCode(),
                        record.source() == null ? null : record.source().name(), "恭喜中奖")
                : DrawResultView.ofMiss("手慢了，奖品已被抽完");
    }
}
