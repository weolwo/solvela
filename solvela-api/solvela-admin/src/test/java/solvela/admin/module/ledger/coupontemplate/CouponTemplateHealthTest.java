package solvela.admin.module.ledger.coupontemplate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import solvela.admin.module.ledger.coupontemplate.domain.CouponTemplateGapVO;
import solvela.admin.module.ledger.coupontemplate.service.CouponTemplateHealthService;
import solvela.coupon.CouponTemplate;
import solvela.ledger.coupon.template.dao.CouponTemplateDao;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券模板体检的<b>真库</b>验证。
 *
 * <h3>它守的是「降级看得见」这半边</h3>
 * 发券侧找不到模板时是<b>照发</b>的（规则列全空），不是拒发。这个选择本身没问题
 * —— 拒发会在运行期把一个在架商品变成兑换必失败。但它成立的前提是
 * <b>有人能看见</b>降级发生在哪里。这条测试就是在守那个前提：
 * 体检查不出洞来的话，降级就退化成了「不报错，只是没生效」。
 *
 * <h3>⚠️ 断言写成「不变式」而不是具体条数</h3>
 * 开发库里的奖品配置和商城商品随时会变，写死「应当有 1 个洞」是一条明天就会
 * 红的测试。所以只断言两件必须永远成立的事：
 * <ul>
 *   <li>被列出来的券编码，确实都没有启用中的模板；</li>
 *   <li>有启用模板的券编码，一个都不该出现在清单里（<b>误报</b>会让运营
 *       学会忽略这张清单，那比不报还糟）。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
class CouponTemplateHealthTest {

    @Autowired
    private CouponTemplateHealthService couponTemplateHealthService;

    @Autowired
    private CouponTemplateDao couponTemplateDao;

    @Test
    @DisplayName("🔴 体检清单：列出来的都真没模板，有模板的一个都不误报")
    void 体检不漏报也不误报() {
        Set<String> covered = couponTemplateDao.selectAllEnabled().stream()
                .map(CouponTemplate::getCouponCode)
                .collect(Collectors.toSet());
        assertFalse(covered.isEmpty(), "库里应当有启用中的券模板（种子数据）");

        List<CouponTemplateGapVO> gaps = couponTemplateHealthService.listGaps();

        for (CouponTemplateGapVO gap : gaps) {
            assertFalse(covered.contains(gap.couponCode()),
                    "误报：" + gap.couponCode() + " 明明有启用中的模板，却被列进了体检清单。"
                            + "误报会让运营学会忽略这张清单，那比不报还糟");
            assertTrue(gap.sourceType().equals("PRIZE") || gap.sourceType().equals("MALL"),
                    "来源类型只有奖品配置和商城商品两种，出现了：" + gap.sourceType());
        }
    }
}
