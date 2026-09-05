package solvela.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.app.domain.OrderView;
import solvela.enums.MallOrderStatusEnum;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallOrderView;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 兑换记录的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <h3>🔴 履约失败原因不原样下发</h3>
 * 域侧的 {@code failReason} 是<b>写给运营看的</b>（「券商品未配置券模编码，
 * 补齐后可重发」）。原样甩给用户，他既看不懂也无从下手。
 * 这里换成一句他能理解、而且知道该找谁的话。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int RECENT_LIMIT = 20;

    private final MallApi mallApi;

    public List<OrderView> listMyOrders(Long memberId) {
        return mallApi.listMyOrders(memberId, RECENT_LIMIT).stream()
                .map(OrderService::toView)
                .toList();
    }

    private static OrderView toView(MallOrderView order) {
        return new OrderView(
                order.orderNo(),
                order.commodityName(),
                order.coverUrl(),
                specs(order.skuAttrs()),
                order.quantity(),
                cost(order.payPoints(), order.payCash()),
                statusText(order.status()),
                status(order.status()),
                hint(order.status()),
                format(order.createTime()));
    }

    /** 规格拼成「颜色：曜石黑」。空 map 是无规格商品，不是异常 */
    private static List<String> specs(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return List.of();
        }
        return attrs.entrySet().stream()
                .map(e -> e.getKey() + "：" + e.getValue())
                .toList();
    }

    /**
     * 对价：<b>积分是整数，现金才是小数。</b>
     *
     * <p>混为一谈的代价是「45000.00 积分」这种展示。
     * 纯积分单不写「+ ¥0.00」—— 那一截只会让人以为还要再付钱。
     */
    private static String cost(Integer points, BigDecimal cash) {
        String left = String.format("%,d 积分", points == null ? 0 : points);
        if (cash == null || cash.compareTo(BigDecimal.ZERO) <= 0) {
            return left;
        }
        return left + String.format(" + ¥%,.2f", cash);
    }

    /**
     * 状态 → 给用户看的话。
     *
     * <p>用 switch 表达式且<b>不写 default</b>：商城域新增状态时这里编译不过，
     * 而不是悄悄显示成「处理中」，让一个失败的单子看起来还在路上。
     */
    private static String statusText(MallOrderStatusEnum status) {
        return switch (status) {
            case UNPAID -> "待支付";
            // 待履约与履约中对用户是同一件事：东西还没到。区分开来只是把内部流程摊给他看
            case PENDING, FULFILLING -> "处理中";
            case FINISHED -> "已完成";
            case CANCELLED -> "已取消";
            case REFUNDED -> "已退款";
            case FAILED -> "发放失败";
        };
    }

    private static String status(MallOrderStatusEnum status) {
        return switch (status) {
            case UNPAID, PENDING, FULFILLING -> "PENDING";
            case FINISHED -> "DONE";
            case CANCELLED, REFUNDED, FAILED -> "FAILED";
        };
    }

    /**
     * 状态之外还要说的那一句。
     *
     * <h3>🔴 失败时必须说清「积分还在不在」</h3>
     * 用户看到「发放失败」第一个念头就是这个。履约失败<b>不退积分</b>
     *（东西还欠着他，不是没买），所以要明说「积分未退回，我们会尽快重新发放」——
     * 不说的话，他会以为积分白扣了，然后去投诉。
     */
    private static String hint(MallOrderStatusEnum status) {
        return switch (status) {
            case UNPAID -> "待支付，超时将自动取消";
            case PENDING, FULFILLING -> "我们正在为你处理";
            case FAILED -> "积分未退回，我们会尽快重新为你发放，也可联系客服";
            case CANCELLED -> "积分已退回";
            case FINISHED, REFUNDED -> null;
        };
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }
}
