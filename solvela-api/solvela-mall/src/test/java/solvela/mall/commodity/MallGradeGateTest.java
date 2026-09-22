package solvela.mall.commodity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.mall.MallCommodity;
import solvela.member.grade.service.MemberGrowthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专享商品的门槛。
 *
 * <h3>三条判据都偏向「不误锁」</h3>
 * 这个守卫错向两边的代价不对称：
 * <ul>
 *   <li>该拦没拦 —— 一个低等级用户换走了专享商品，运营看得见、能补救；</li>
 *   <li><b>不该拦却拦了</b> —— 一件本该人人可兑的商品谁都兑不了，
 *       而它不报错、不打日志，表现是「这个商品怎么没人换」。</li>
 * </ul>
 * 所以 null / 负数一律当 0（不限）。
 *
 * @Date 2026-09-22
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallGradeGateTest {

    @Mock
    private MemberGrowthService memberGrowthService;

    @InjectMocks
    private MallGradeGate gate;

    private static MallCommodity commodity(Integer minGrade) {
        MallCommodity c = new MallCommodity();
        c.setMinGrade(minGrade);
        return c;
    }

    @Test
    @DisplayName("不限等级的商品：谁都能兑，连等级都不用查")
    void 不限等级时直接放行() {
        assertTrue(gate.canRedeem(1L, commodity(0)));
        // 没有门槛就不该为它查一次库 —— 商品列表是热路径
        verify(memberGrowthService, never()).currentGrade(1L);
    }

    @Test
    @DisplayName("🔴 minGrade 为 null 当作不限，不是当作「最高等级」")
    void null当作不限() {
        // 这一列是后加的。实体是 Integer，从别处 new 出来的对象可能没设值 ——
        // 判成「有门槛」的话，一批老商品会突然谁都兑不了
        assertTrue(gate.canRedeem(null, commodity(null)));
        assertEquals(0, MallGradeGate.requiredGrade(commodity(null)));
        assertFalse(MallGradeGate.isExclusive(commodity(null)));
    }

    @Test
    @DisplayName("等级够：放行；等级不够：拦住")
    void 按当前等级判定() {
        when(memberGrowthService.currentGrade(1L)).thenReturn(3);

        assertTrue(gate.canRedeem(1L, commodity(3)), "正好达标要放行，门槛是「≥」不是「>」");
        assertTrue(gate.canRedeem(1L, commodity(2)));
        assertFalse(gate.canRedeem(1L, commodity(4)));
    }

    @Test
    @DisplayName("🔴 没登录按 0 级算，不是直接放行")
    void 匿名按零级() {
        /*
         * 商品列表匿名可看。放行的话，未登录用户会看到「可兑换」，
         * 点进去走完注册才发现兑不了 —— 那一步的挫败感比一开始就标明大得多。
         */
        assertFalse(gate.canRedeem(null, commodity(1)));
        assertTrue(gate.canRedeem(null, commodity(0)));
        assertEquals(0, gate.gradeOf(null));
    }

    @Test
    @DisplayName("🔴 保级缓冲期：他【在】白金就按白金算，不看成长值够不够")
    void 缓冲期按当前等级() {
        /*
         * currentGrade 返回的是「他在哪一档」，不是「成长值够到哪一档」。
         * 缓冲期里两者不一样 —— 而缓冲期本来就是挽留，
         * 这时候把专享权益收走，等于亲手把要挽留的人推走。
         */
        when(memberGrowthService.currentGrade(1L)).thenReturn(3);

        assertTrue(gate.canRedeem(1L, commodity(3)));
    }

    @Test
    @DisplayName("查不到等级（currentGrade 返回 null）按 0 级，不抛")
    void 等级为null时按零级() {
        when(memberGrowthService.currentGrade(1L)).thenReturn(null);

        assertEquals(0, gate.gradeOf(1L));
        assertFalse(gate.canRedeem(1L, commodity(1)));
        assertTrue(gate.canRedeem(1L, commodity(0)));
    }
}
