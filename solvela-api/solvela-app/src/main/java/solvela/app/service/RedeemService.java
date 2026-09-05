package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.domain.RedeemRequest;
import solvela.app.domain.RedeemResultView;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.enums.MallOrderStatusEnum;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemReason;
import solvela.marketing.api.MallRedeemResult;

/**
 * 兑换的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <p>库存、限兑、积分够不够，全部由商城域说了算 —— 本类一行都不判。
 * 它只做网关该做的两件事：把拒绝原因翻成 HTTP 状态码与<b>给用户看的话</b>，
 * 把域的结果组装成 C 端的形状。与抽奖那条链路同一套分工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedeemService {

    private final MallApi mallApi;

    public RedeemResultView redeem(Long memberId, RedeemRequest request) {
        MallRedeemResult result = mallApi.redeem(new MallRedeemCmd(
                memberId, request.skuId(), request.quantityOrOne(), request.addressId()));

        if (!result.accepted()) {
            throw translate(result.reason());
        }
        return new RedeemResultView(result.orderNo(), result.status().getValue(),
                message(result.status()));
    }

    /**
     * 受理之后给用户看的那句话。
     *
     * <h3>🔴 待支付要如实说</h3>
     * 现金部分<b>没有支付链路</b>（后端一行支付代码都没有），所以 payType=2 的订单
     * 落在 UNPAID。这里必须说「待支付」而不是「兑换成功」——
     * 说成功的话用户会等着收货，而那单永远不会被履约。
     */
    private static String message(MallOrderStatusEnum status) {
        return switch (status) {
            case UNPAID -> "兑换单已创建，待支付";
            case PENDING -> "兑换成功，我们会尽快为你处理";
            case FULFILLING -> "兑换成功，正在为你发放";
            case FINISHED -> "兑换成功";
            // 下面三个不该出现在「刚受理」的返回里；真出现了说明域侧状态机变了
            case CANCELLED, REFUNDED, FAILED -> {
                log.error("【兑换】刚受理的订单落在了 {} 状态 —— 域侧状态机可能改过了", status);
                yield "兑换已提交";
            }
        };
    }

    /**
     * 拒绝原因 → HTTP 契约。
     *
     * <p>用 switch 表达式：商城域新增一个 {@link MallRedeemReason} 时<b>这里编译不过</b>，
     * 而不是悄悄落进兜底分支返回一句「服务开小差了」。跨服务之后这一点更重要 ——
     * 两边是分开发版的，编译期能拦住的东西不该留到运行期。
     *
     * <h3>为什么「商品下架」和「SKU 不存在」说同一句话</h3>
     * 对用户是同一件事：<b>兑不了了</b>。区分开来既没用，
     * 又等于告诉爬虫哪些 id 是有效的。
     */
    private static ApiException translate(MallRedeemReason reason) {
        return switch (reason) {
            case SKU_NOT_FOUND, COMMODITY_OFF ->
                    new ApiException(ApiErrors.NOT_FOUND, "商品不存在或已下架");
            case OUT_OF_STOCK -> new ApiException(ApiErrors.CONFLICT, "手慢了，该规格已兑完");
            case EXCHANGE_LIMITED -> new ApiException(ApiErrors.CONFLICT, "已达到该商品的兑换上限");
            // 差多少由前端算并展示 —— 它手上有余额和价格，比这里再查一次便宜
            case POINTS_NOT_ENOUGH -> new ApiException(ApiErrors.CONFLICT, "积分不足");
            case WALLET_UNAVAILABLE ->
                    new ApiException(ApiErrors.CONFLICT, "账户暂时不可用，请联系客服");
            // 可重试，措辞要让用户知道再点一次就行，别说成「失败」
            case CONCURRENT_CONFLICT ->
                    new ApiException(ApiErrors.CONFLICT, "账户余额变动中，请重试");
            case ADDRESS_REQUIRED -> new ApiException(ApiErrors.CONFLICT, "请选择收货地址");
            case ADDRESS_NOT_FOUND ->
                    new ApiException(ApiErrors.CONFLICT, "收货地址已失效，请重新选择");
            case INTERNAL -> {
                // 这是我们自己的问题，用户没有任何办法让它发生，所以必须留痕
                log.error("【兑换】域侧返回 INTERNAL —— 去营销服务的日志里找真正的原因");
                yield new ApiException(ApiErrors.INTERNAL, "兑换失败，请稍后再试");
            }
        };
    }
}
