package solvela.mall.commodity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.mall.MallCommodity;
import solvela.member.grade.service.MemberGrowthService;

/**
 * 专享商品的门槛：<b>这件商品，这个人兑不兑得了</b>。
 *
 * <h3>🔴 只有这一处判，展示和下单共用</h3>
 * 与 {@link MallPricing} 同一条理由：C 端要知道「该不该打上专享标」，
 * 下单要知道「该不该拒」。两边分别实现的后果是<b>页面说能兑、点下去说不行</b>，
 * 或者更糟 —— 页面说不能兑，而直接打接口能兑得了。
 *
 * <h3>⚠️ 展示上「藏」还是「标」，是两种产品取向，这里选「标」</h3>
 * 任务中心那边是<b>藏</b>的（{@code TaskCenterProviderImpl.hideGradeLocked}）：
 * 一个点不动的任务对用户没有任何吸引力。
 * 商城反过来 —— 一件<b>看得见但换不了</b>的商品，正是「够上去」的理由本身，
 * 而给用户一个够上去的理由，就是这套等级体系要换的东西。
 *
 * <p>所以两处判断不同是刻意的，不是遗漏。
 *
 * <h3>🔴 但无论藏还是标，服务端都必须照样拦</h3>
 * 列表标了、按钮灰了，都只是<b>界面</b>。直接打下单接口是绕不过去的那条路，
 * 而那条路上只有这个守卫。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallGradeGate {

    private final MemberGrowthService memberGrowthService;

    /**
     * 这件商品要求的最低等级。{@code 0} = 不限。
     *
     * <p>⚠️ 兼容 null：{@code min_grade} 是后加的列（2026-09-22），
     * 虽然 DDL 上是 NOT NULL DEFAULT 0，但实体是 Integer，
     * 而<b>从别处 new 出来的 MallCommodity</b>（测试、拷贝）可能没设值。
     * 把 null 当 0 是安全方向：不限比误锁好 —— 误锁是一件本该能兑的商品兑不了。
     */
    public static int requiredGrade(MallCommodity commodity) {
        Integer min = commodity == null ? null : commodity.getMinGrade();
        return min == null || min < 0 ? 0 : min;
    }

    /** 这件商品是不是专享的（有等级门槛） */
    public static boolean isExclusive(MallCommodity commodity) {
        return requiredGrade(commodity) > 0;
    }

    /**
     * 这个人够不够得着。
     *
     * <p>🔴 {@code memberId} 为 null（<b>没登录</b>）时按 0 级算，而不是直接放行。
     * 商品列表是匿名可看的，放行的话未登录用户会看到「可兑换」，
     * 点进去登录完才发现兑不了 —— 而那一步已经走了注册流程。
     *
     * <p>⚠️ 取的是 {@code currentGrade}（他<b>在</b>哪一档），不是「成长值够到哪一档」。
     * 保级缓冲期里两者不一样：他挂着白金但成长值够不着白金，
     * 而此刻他<b>确实是</b>白金会员，专享商品就该能兑 —— 缓冲期本来就是挽留，
     * 这时候把权益收走，等于亲手把要挽留的人推走。
     */
    public boolean canRedeem(Long memberId, MallCommodity commodity) {
        int required = requiredGrade(commodity);
        if (required == 0) {
            return true;
        }
        return gradeOf(memberId) >= required;
    }

    /** 当前等级。没登录、没成长值行都算 0 级 */
    public int gradeOf(Long memberId) {
        if (memberId == null) {
            return 0;
        }
        Integer grade = memberGrowthService.currentGrade(memberId);
        return grade == null ? 0 : grade;
    }
}
