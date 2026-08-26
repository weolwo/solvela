package solvela.risk.spi;

import solvela.risk.PromotionConfig;
import solvela.risk.ProposalRecord;

/**
 * 风控提案通过后的<b>资产派发契约</b>，由 ledger 实现（{@code AssetDispatchEngine}）。
 *
 * <h3>为什么要有这个接口</h3>
 * 风控（risk）与账务（ledger）之间原本是<b>互相引用</b>的：
 * <ul>
 *   <li>ledger 要读提案与活动配置 —— {@code AssetDispatchEngine} 依赖 risk 的两个 Dao；</li>
 *   <li>risk 提案通过后要派发资产 —— {@code ProposalRecordService} 依赖 ledger 的引擎。</li>
 * </ul>
 * 两个包在同一个 jar 里时，这个环编译器不会拦，也就一直没人发现。一旦按域拆成独立模块，
 * 它会直接让 maven 报循环依赖 —— <b>把两个域永久钉死在同一个模块里</b>。
 *
 * <p>用依赖倒置断掉：<b>risk 定契约，ledger 实现</b>。这样只剩 ledger → risk 一个方向。
 * 写法与 {@code activity.spi.ActivityRefProvider} 一致，那里的注释把同一个道理讲过一遍。
 *
 * <p>换个角度看更清楚：这两个域将来若真拆成独立服务，它们之间只能是<b>单向调用或事件</b>，
 * 不可能同步互调。接口在这里的作用，就是让代码结构提前符合那个约束。
 *
 * <p>⚠️ 行为没有变化：仍然是同一个实例、同一次同步调用，只是穿过了一个接口。
 */
public interface AssetDispatcher {

    /**
     * 按提案派发资产。事务边界与重试语义见实现类。
     */
    void execute(ProposalRecord proposal, PromotionConfig config);
}
