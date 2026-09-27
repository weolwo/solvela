package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.service.UnreadCounter;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.MallApi;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.DeliveryApi;
import solvela.member.api.DeliverySummaryView;
import solvela.member.api.MemberGradeApi;

import java.util.function.Supplier;

/**
 * 「我的」页一屏要的全部数字，一次给齐。
 *
 * <h3>为什么要它</h3>
 * 2026-09-27 「我的」页重排成「一眼看到自己有什么」之后，前端第一版是各拉一份列表再数：
 * 进这一页多 5 个请求，而且<b>数错</b> —— 实物单、彩票的列表接口有条数上限，
 * 单子多的会员数出来的是「最近 N 条里有几张」。现在每个数都由各自的域用 COUNT 口径给。
 *
 * <h3>为什么在网关聚合，不在 app-biz 里聚合</h3>
 * 这些数分属营销（彩票、收藏）、资产（券、实物单）、会员（等级、消息）几个域，而资产域是要独立出去的那一个。
 * 在 app-biz 里写一个跨域的汇总，等于在将来要切开的那条缝上打一个结；
 * 网关本来就是给 C 端拼数据的地方，这里每个域只各自回答「我这一个数是多少」。
 *
 * <h3>任何一个数拿不到，都只让那一个数变成 null</h3>
 * 前端对 null 显示「—」，入口照样在。一个下游抖一下就让整块 500，是拿次要目标伤害主要目标。
 *
 * <h3>串行调用</h3>
 * 七次调用都是同机房到 app-biz 的点查，串行几十毫秒。并行要把设备号、链路号这些
 * 绑在请求线程上的上下文重新绑到别的线程上，为这点延迟不值得，真成了瓶颈再说。
 */
@Slf4j
@Tag(name = "我的")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final UnreadCounter unreadCounter;
    private final MemberGradeApi memberGradeApi;
    private final CouponQueryApi couponQueryApi;
    private final ActivityApi activityApi;
    private final DeliveryApi deliveryApi;
    private final MallApi mallApi;

    /**
     * 「我的」页的数字。
     *
     * @param unread         消息未读（通知 + 公告）
     * @param gradeName      当前等级名
     * @param coupons        可用券张数（口径同券包「可用」tab）
     * @param lotteryTickets 彩票号码张数（跨玩法跨期）
     * @param favorites      收藏的商品件数（只数仍可见的）
     * @param deliveries     实物单按「要不要我动手」分组
     */
    public record MeSummaryView(Long unread, String gradeName, Long coupons, Long lotteryTickets,
                                Long favorites, DeliverySummaryView deliveries) {
    }

    @GetMapping("/summary")
    public MeSummaryView summary() {
        Long memberId = CurrentMember.require().memberId();
        return new MeSummaryView(
                part("unread", () -> unreadCounter.count(memberId)),
                part("grade", () -> memberGradeApi.myGrade(memberId).gradeName()),
                part("coupons", () -> couponQueryApi.countUsable(memberId)),
                part("lotteryTickets", () -> activityApi.countMyLotteryTickets(memberId)),
                part("favorites", () -> mallApi.countFavorites(memberId)),
                part("deliveries", () -> deliveryApi.summary(memberId)));
    }

    /** 取一个数；失败只让这一个数变成 null，并留一行日志（带链路号，能查到是哪个下游） */
    private static <T> T part(String name, Supplier<T> loader) {
        try {
            return loader.get();
        } catch (RuntimeException e) {
            log.warn("[我的] 汇总里 {} 取不到，这一项给 null: {}", name, e.toString());
            return null;
        }
    }
}
