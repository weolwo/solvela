package solvela.marketing.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充话费（外部场景消费）的对外契约。实现在 {@code solvela-external}。
 *
 * <h3>为什么契约在 marketing-api 而不是一个新的 external-api</h3>
 * 本模块的粒度<b>对齐服务，不对齐 maven 模块</b>（见 member-api 的 pom：
 * 「所以不会再有 solvela-ledger-api」）。外部场景和商城将来同属
 * app-activity 那个服务，契约就该在一起。
 *
 * <h3>⚠️ 今天运营商那一端是<b>假的</b></h3>
 * 它存在是为了证明「券能被一个<b>外部场景</b>消费掉」这条路是通的，
 * 也就是券模板里 {@code scope_type = EXTERNAL} 那一档不是摆设。
 * 充话费能力本身要接运营商，是独立一块（方案 §6.2）。
 *
 * <p>🔴 假充值<b>配到生产会启动失败</b>
 *（{@code ExternalRechargeService.checkTransport}）——
 * 它比假支付还危险一档：用户花了钱、券也用了，系统说充值成功，
 * 而话费一分钱都没到账，<b>每一张表看起来都正常</b>。
 */
@HttpExchange("/internal/recharge")
public interface RechargeApi {

    /** 这个场景的可选面额与最低金额。关闭时 {@code enabled = false} */
    @GetExchange("/options")
    RechargeOptionsView options();

    /**
     * 选券：这一笔充值能用哪些券。
     *
     * <p>走的是和商城下单页<b>同一个</b>试算接口，只是按 {@code sceneCode} 匹配。
     */
    @PostExchange("/trial")
    RechargeTrialView trial(@RequestParam Long memberId, @RequestParam BigDecimal amount);

    /**
     * 下单：试算 → 锁券 → 落一张待支付单。
     *
     * <p>⚠️ {@code memberId} 由<b>调用方从登录态取</b>。
     */
    @PostExchange("/order")
    RechargeOrderResult create(@RequestBody RechargeOrderCmd cmd);

    /**
     * 支付并执行。
     *
     * <p>⚠️ 今天支付和执行都是假的，所以合在一个动作里。
     * 真接了之后它们必然拆开（支付是回调进来的，执行是异步出去的），
     * 但<b>状态机现在就按拆开的样子建</b>，那一天改的是调用顺序，不是表结构。
     */
    @PostExchange("/order/{orderNo}/pay")
    RechargeOrderResult pay(@PathVariable String orderNo, @RequestParam Long memberId);

    /** 我的充值单，新的在前 */
    @GetExchange("/order")
    List<RechargeOrderView> listMyOrders(@RequestParam Long memberId, @RequestParam int limit);
}
