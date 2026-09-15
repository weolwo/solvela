package solvela.admin.module.ledger.coupontemplate.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import solvela.admin.module.ledger.coupontemplate.domain.CouponTemplateGapVO;
import solvela.coupon.CouponTemplate;
import solvela.enums.EnableStatusEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.ledger.coupon.template.dao.CouponTemplateDao;
import solvela.mall.MallCommodity;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.dao.PrizeConfigDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 券模板体检：找出<b>会发券但没有模板</b>的配置点。
 *
 * <h3>🔴 它存在的理由：发券侧是「降级」而不是「拒发」</h3>
 * {@code CouponIssueService} 在找不到模板时照发，只是规则列全空 ——
 * 因为拒发会在运行期把一个在架商品变成兑换必失败（库里真有这种商品：
 * 「华为音乐 音乐VIP（年卡）」发的是<b>兑换凭证</b>，本来就没有「减多少」这回事）。
 *
 * <p>但降级必须<b>看得见</b>，否则就变成这个仓库一直在骂的那种「不报错，只是没生效」：
 * 券照发、用户照收，直到有人拿它去抵扣才发现减不出钱。日志里那条 ERROR
 * 要有人去翻才看得到，所以再给运营一个<b>不用翻日志</b>的入口。
 *
 * <h3>为什么这个查询在 admin 而不在 ledger</h3>
 * 它要同时看<b>奖品配置</b>和<b>商城商品</b>，而账务域不能依赖营销域和商城域
 *（有架构守卫测试盯着）。admin 本来就是那个能看见所有域的聚合端。
 *
 * <h3>⚠️ 它查的是「配置点」，不是「已经发出去的券」</h3>
 * 目的是<b>在发之前</b>发现问题。存量那 845 张没有规则的券是另一件事，
 * 见方案 §10.2（作废重发 / 人工回填 / 一律按无门槛 —— 最后那个是资损，别选）。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponTemplateHealthService {

    /** 奖品配置里的券类型取值，与 {@code PrizeTypeEnum.COUPON} 对应 */
    private static final String PRIZE_TYPE_COUPON = "COUPON";

    /** 商城商品里的券类型取值 */
    private static final String COMMODITY_TYPE_COUPON = "COUPON";

    private final CouponTemplateDao couponTemplateDao;
    private final PrizeConfigDao prizeConfigDao;
    private final MallCommodityDao mallCommodityDao;

    /**
     * 列出所有「在用、会发券、但没有启用中模板」的配置点。
     *
     * <p>只看<b>启用 / 在架</b>的：已经停用的奖品和下架的商品不会再发券了，
     * 把它们列出来只会让这张清单长到没人看。
     */
    public List<CouponTemplateGapVO> listGaps() {
        Set<String> covered = couponTemplateDao.selectAllEnabled().stream()
                .map(CouponTemplate::getCouponCode)
                .collect(Collectors.toSet());

        List<CouponTemplateGapVO> gaps = new ArrayList<>();

        prizeConfigDao.selectList(Wrappers.<PrizeConfig>lambdaQuery()
                        .eq(PrizeConfig::getPrizeType, PRIZE_TYPE_COUPON)
                        .eq(PrizeConfig::getStatus, EnableStatusEnum.ENABLED))
                .stream()
                // prize_code 就是券模编码 —— 发奖链路把它当 assetRef 传给账务域
                .filter(prize -> StringUtils.isNotBlank(prize.getPrizeCode()))
                .filter(prize -> !covered.contains(prize.getPrizeCode()))
                .forEach(prize -> gaps.add(new CouponTemplateGapVO(
                        prize.getPrizeCode(), "PRIZE", prize.getPrizeCode(), prize.getPrizeName())));

        mallCommodityDao.selectList(Wrappers.<MallCommodity>lambdaQuery()
                        .eq(MallCommodity::getCommodityType, COMMODITY_TYPE_COUPON)
                        .eq(MallCommodity::getStatus, MallCommodityStatusEnum.ON))
                .stream()
                .filter(commodity -> StringUtils.isNotBlank(commodity.getAssetRef()))
                .filter(commodity -> !covered.contains(commodity.getAssetRef()))
                .forEach(commodity -> gaps.add(new CouponTemplateGapVO(
                        commodity.getAssetRef(), "MALL",
                        commodity.getCommodityCode(), commodity.getCommodityName())));

        return gaps;
    }
}
