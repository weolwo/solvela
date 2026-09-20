package solvela.mall.order.event;

import org.springframework.stereotype.Component;
import solvela.event.BizActionCodes;
import solvela.mall.MallOrder;
import solvela.mall.order.dao.MallOrderDao;
import solvela.marketing.api.BizActionAuditProvider;
import solvela.marketing.api.BizActionRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城侧的<b>事后清单</b>：窗口内哪些单真的付过款。
 *
 * <h3>它回答的是一个问题，不执行任何补推</h3>
 * 补推由任务域的对账 job 做 —— 本类<b>一个字都不认识任务引擎</b>
 * （{@code PlayBoundaryTest} 扫本模块的字节码常量池，出现 {@code solvela/task/} 就红）。
 * 这里只实现 {@code solvela-marketing-api} 里的一个 SPI，
 * 而那个包商城本来就依赖着（{@code MallApi} 也在里面）。
 *
 * <h3>为什么方向是"任务域来拉"</h3>
 * 商城没法知道自己漏了谁 —— 答案在 {@code t_task_record_flow} 里，
 * 而那张表商城既读不到、也不该读到。
 *
 * <p>详见 {@link BizActionAuditProvider} 的类注释。
 *
 * @author alaric
 * @date 2026-09-17
 */
public final class MallOrderAuditProvider {

    private MallOrderAuditProvider() {
    }

    /**
     * 两个动作码共用同一份查询 —— 它们<b>同时产生、同一批数据</b>
     * （见 {@link MallOrderActionPublisher#publishOrderPaid}），
     * 所以漏也是一起漏，补也该一起补。
     *
     * <p>做成两个 bean 而不是一个 bean 报两个码，是因为 SPI 约定
     * 「一个动作只有一个提供方」——那条约定让对账 job 不必处理
     * 「两个 provider 都说自己管 ORDER_PAID」这种歧义。
     */
    private abstract static class Base implements BizActionAuditProvider {

        private final MallOrderDao mallOrderDao;

        Base(MallOrderDao mallOrderDao) {
            this.mallOrderDao = mallOrderDao;
        }

        @Override
        public List<BizActionRecord> listSettled(LocalDateTime from, LocalDateTime to, int limit) {
            List<MallOrder> settled = mallOrderDao.selectSettledBetween(from, to, limit);
            return settled.stream().map(Base::toRecord).toList();
        }

        /**
         * 🔴 三个字段都必须和打点那一刻<b>完全一致</b>，否则补推是有害的而不是无害的：
         * <ul>
         *   <li>{@code bizId} 用订单号 —— 对不上唯一键就挡不住，同一笔单被算两次；</li>
         *   <li>{@code occurredAt} 用 {@code pay_time} 而不是"现在" ——
         *       传现在的话，昨晚漏掉的那一单会被记进今天，用户昨天的进度凭空少一次；</li>
         *   <li>{@code payload} 走 {@link MallOrderActionPublisher#payloadOf} 这<b>同一个</b>
         *       方法 —— 计额型任务按 {@code metric_source} 从里面取数，
         *       两份实现差一个键名，补出来的进度就和正常链路对不上。</li>
         * </ul>
         */
        private static BizActionRecord toRecord(MallOrder order) {
            return new BizActionRecord(
                    order.getOrderNo(),
                    order.getMemberId(),
                    order.getPayTime(),
                    MallOrderActionPublisher.payloadOf(order));
        }
    }

    /** 计次口径：「下单 3 次」这类任务 */
    @Component
    static class OrderPaid extends Base {
        OrderPaid(MallOrderDao mallOrderDao) {
            super(mallOrderDao);
        }

        @Override
        public String supportActionCode() {
            return BizActionCodes.ORDER_PAID;
        }
    }

    /** 计额口径：「累计消费 500 分」这类任务 */
    @Component
    static class OrderAmount extends Base {
        OrderAmount(MallOrderDao mallOrderDao) {
            super(mallOrderDao);
        }

        @Override
        public String supportActionCode() {
            return BizActionCodes.ORDER_AMOUNT;
        }
    }
}
