package solvela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallDeliveryFillResult;
import solvela.member.api.DeliveryApi;
import solvela.member.api.MemberDeliveryView;

import java.util.List;

/**
 * 我的实物奖品：中奖 / 兑换的实物寄到哪了。
 *
 * <h3>它补的是三段式履约里一直缺的那一步</h3>
 * {@code PhysicalAssetHandler} 的类注释把实物履约写成三段，
 * 第 ② 段是「用户在 C 端补填收货信息」—— 而那个入口从来没有存在过。
 * 中了实物奖之后用户侧是断的：看不到、也填不了地址，只能等客服。
 *
 * <h3>🔴 两个动作走两个契约，不是随手分的</h3>
 * <ul>
 *   <li><b>读</b>走 {@link DeliveryApi}（资产域）—— 履约单是它的表；</li>
 *   <li><b>补填</b>走 {@link MallApi}（商城域）—— 因为只有商城解析得了
 *       {@code addressId}，地址簿是它的表。资产域那一侧收的是明文三件套。</li>
 * </ul>
 *
 * <p>让网关自己去读地址再转发<b>行不通</b>：{@code MallApi.getAddress} 回来的
 * 手机号是<b>脱敏值</b>（{@code 138****8000}），拿它当收件电话等于把包裹
 * 寄给一个打不通的号码。这不是绕一下就能解决的，是脱敏设计本身的结果。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Tag(name = "我的实物奖品")
@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    /** 一页给多少。实物单量天然很小，不做分页 —— 真多到要翻页时再说 */
    private static final int DEFAULT_LIMIT = 50;

    private final DeliveryApi deliveryApi;
    private final MallApi mallApi;

    /**
     * 我的实物奖品。中奖的和商城兑的<b>混在一起</b> ——
     * 用户不关心「这件东西是抽中的还是兑的」，他只想知道「我的东西呢」。
     *
     * <p>没有就返回空数组，不是 404。
     */
    @Operation(summary = "我的实物奖品（含中奖与商城兑换）")
    @GetMapping
    public List<MemberDeliveryView> listMine() {
        return deliveryApi.listMine(CurrentMember.require().memberId(), DEFAULT_LIMIT);
    }

    /**
     * 用地址簿里的一个地址补填收货信息。
     *
     * <h3>🔴 memberId 从登录态取，绝不接受客户端传</h3>
     * 它同时是<b>两道</b>归属校验的入参：地址是不是你的、履约单是不是你的。
     * 让客户端传的话，两道一起失效 —— 表现是「能拿别人的地址 id 填进自己的单，
     * 再查一次就读出了别人住哪」。
     *
     * <h3>为什么只收 addressId，不收姓名电话地址三个字段</h3>
     * 让端上传明文的话，「寄到哪」就成了客户端说了算，而服务端无从校验 ——
     * 和商城下单只收 {@code addressId} 是同一条规矩。
     * 用户要寄到新地址，先去地址簿加一条。
     */
    @Operation(summary = "用地址簿里的地址补填收货信息")
    @PostMapping("/{deliveryId}/address")
    public MallDeliveryFillResult fillAddress(@PathVariable Long deliveryId,
                                              @RequestParam Long addressId) {
        return mallApi.fillDeliveryAddress(deliveryId, CurrentMember.require().memberId(), addressId);
    }
}
