package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.EnableStatusEnum;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.enums.ReviewLevelEnum;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.dao.LotteryConfigDao;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 彩票玩法与优惠配置枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>这一批做完，{@code EnumMigrationRatchetTest} 的名单里就再没有
 * private 作用域的状态魔法常量了 —— 剩下的全在 MallConst / MemberConst 这类公共常量类里。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class LotteryConfigEnumMappingTest {

    @Autowired
    private LotteryConfigDao lotteryConfigDao;

    @Autowired
    private PromotionConfigDao promotionConfigDao;

    @Test
    @DisplayName("彩票玩法：status 能装配，上下线两态都出现过")
    void 玩法配置装配() {
        List<LotteryConfig> list = lotteryConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_lottery_config 没有数据，这条用例失去意义");
        for (LotteryConfig e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 库里是 0×2 / 1×1
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == LotteryConfigStatusEnum.ONLINE));
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == LotteryConfigStatusEnum.OFFLINE));
    }

    @Test
    @DisplayName("彩票玩法：按枚举过滤，分状态计数之和等于总量")
    void 按状态过滤玩法() {
        List<LotteryConfig> all = lotteryConfigDao.selectList(null);
        assertFalse(all.isEmpty());

        int sum = 0;
        for (LotteryConfigStatusEnum status : LotteryConfigStatusEnum.values()) {
            List<LotteryConfig> hit = lotteryConfigDao.selectList(
                    new LambdaQueryWrapper<LotteryConfig>().eq(LotteryConfig::getStatus, status));
            for (LotteryConfig e : hit) {
                assertEquals(status, e.getStatus(), "按 " + status + " 查询却查出了 " + e.getStatus());
            }
            sum += hit.size();
        }
        assertEquals(all.size(), sum, "分状态计数之和与总量对不上");
    }

    @Test
    @DisplayName("优惠配置：status 与 review_level 两列都能装配")
    void 优惠配置装配() {
        List<PromotionConfig> list = promotionConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_promotion_config 没有数据，这条用例失去意义");
        for (PromotionConfig e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
            assertNotNull(e.getReviewLevel(), "reviewLevel 装配成了 null");
        }
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED));
    }

    @Test
    @DisplayName("review_level 是有限取值而不是数值等级")
    void 审核层级是有限取值() {
        List<PromotionConfig> list = promotionConfigDao.selectList(null);
        assertFalse(list.isEmpty());

        // 名字里带 level，改造初期的自动分类把它误判成「开放数值等级」而漏了一轮。
        // 这条断言把「它只有三个取值」这个事实钉住。
        for (PromotionConfig e : list) {
            assertTrue(e.getReviewLevel() == ReviewLevelEnum.NONE
                            || e.getReviewLevel() == ReviewLevelEnum.SINGLE
                            || e.getReviewLevel() == ReviewLevelEnum.DOUBLE,
                    "reviewLevel 落在枚举之外：" + e.getReviewLevel());
        }
        assertEquals(3, ReviewLevelEnum.values().length, "审核层级不该有第四档，加档要同步 ProposalStatusEnum 的流转");
    }
}
