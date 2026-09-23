package solvela.mall.commodity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.exception.BusinessException;
import solvela.mall.MallCommodity;
import solvela.mall.MallGradePrice;
import solvela.mall.MallSku;
import solvela.mall.commodity.MallPricing;
import solvela.mall.commodity.dao.MallGradePriceDao;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.sku.manager.MallSkuManager;

import java.util.List;

/**
 * 单品覆盖价的管理端写入口。
 *
 * <h3>🔴 这里配的每一行<b>都会真的少收钱</b></h3>
 * 和改商品名、换封面不是一回事：保存之后<b>下一次兑换就按这个价扣分</b>。
 * 所以四条校验都在这里，而且都是<b>硬拦</b>而不是警告 ——
 * 一条配错的覆盖价不会报错，只会安静地一单一单少收，
 * 而发现它的时刻通常是月底对账。
 *
 * @author alaric
 * @date 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallGradePriceService {

    /** 整个商品那一行的 sku_id 哨兵 */
    public static final long WHOLE_COMMODITY = 0L;

    private final MallGradePriceDao mallGradePriceDao;
    private final MallCommodityManager mallCommodityManager;
    private final MallSkuManager mallSkuManager;

    /** 一件商品的全部覆盖价，按等级、再按规格排 */
    public List<MallGradePrice> listByCommodity(Long commodityId) {
        return mallGradePriceDao.selectList(new LambdaQueryWrapper<MallGradePrice>()
                .eq(MallGradePrice::getCommodityId, commodityId)
                .orderByAsc(MallGradePrice::getGradeCode)
                .orderByAsc(MallGradePrice::getSkuId));
    }

    /**
     * 新增或修改一行。
     *
     * @param operator 操作人，落审计列
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(MallGradePrice form, String operator) {
        MallCommodity commodity = check(form);

        MallGradePrice existing = mallGradePriceDao.selectOne(new LambdaQueryWrapper<MallGradePrice>()
                .eq(MallGradePrice::getCommodityId, form.getCommodityId())
                .eq(MallGradePrice::getSkuId, form.getSkuId())
                .eq(MallGradePrice::getGradeCode, form.getGradeCode())
                .last("LIMIT 1"));
        if (existing == null) {
            form.setId(null);
            form.setCreateBy(operator);
            mallGradePriceDao.insert(form);
        } else {
            /*
             * ⚠️ 同一个 (商品, 规格, 等级) 改价而不是再插一行。
             * 唯一键也会拦住重复插入，但那时用户看到的是一句数据库异常，
             * 而他想做的事（改价）本来是合法的。
             */
            form.setId(existing.getId());
            form.setUpdateBy(operator);
            mallGradePriceDao.updateById(form);
        }
        log.info("【商城覆盖价】已保存：商品 {}（{}）规格 {} 等级 {} → {} 分，挂牌 {} 分，操作人={}",
                commodity.getCommodityCode(), commodity.getCommodityName(),
                form.getSkuId() == WHOLE_COMMODITY ? "全部" : form.getSkuId(),
                form.getGradeCode(), form.getPointsPrice(), listPriceOf(form, commodity), operator);
    }

    /** 删一行。删掉之后这一档落回等级折扣率，不是落回原价 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String operator) {
        MallGradePrice existing = mallGradePriceDao.selectById(id);
        if (existing == null) {
            return;
        }
        mallGradePriceDao.deleteById(id);
        log.info("【商城覆盖价】已删除：商品 {} 规格 {} 等级 {}，这一档落回等级折扣率，操作人={}",
                existing.getCommodityId(), existing.getSkuId(), existing.getGradeCode(), operator);
    }

    /**
     * 🔴 四条校验，每一条对应一种「配完看起来没问题、实际不是那个意思」。
     */
    private MallCommodity check(MallGradePrice form) {
        if (form.getCommodityId() == null || form.getGradeCode() == null
                || form.getPointsPrice() == null) {
            throw new BusinessException("商品、等级、价格都不能为空");
        }
        if (form.getSkuId() == null) {
            // 0 是「整个商品」，null 不是。让它落成 null 会在唯一键上开一个洞（见 MallGradePrice.skuId）
            form.setSkuId(WHOLE_COMMODITY);
        }
        if (form.getGradeCode() <= 0) {
            /*
             * ① 0 档不能配。新会员就享特价的话，等级这件事本身就不值钱了 ——
             *    而且它会让「升级能省多少」在 0→1 之间变成负的。
             *    与 MemberGradeConfigService 里那条「0 档不能打折」是同一条规则。
             */
            throw new BusinessException("最低档（等级 0）不能配覆盖价 —— "
                    + "新会员就享特价的话，升级这件事本身就不值钱了");
        }
        if (form.getPointsPrice() < 0) {
            throw new BusinessException("覆盖价不能为负");
        }

        MallCommodity commodity = mallCommodityManager.getById(form.getCommodityId());
        if (commodity == null) {
            throw new BusinessException("商品不存在，可能已被删除");
        }
        if (!MallPricing.participates(commodity)) {
            /*
             * ② 商品已经退出等级折扣。这时存进去的行【一分钱都不会生效】
             *    （MallPricing.participates 一票否决），而运营会以为自己配好了。
             *    宁可在这里说清楚，也不要留一行安静的死配置。
             */
            throw new BusinessException("这件商品已关闭「等级折扣」开关，覆盖价不会生效 —— "
                    + "要配价请先打开那个开关");
        }

        /*
         * ③ 规格必须属于这件商品。不校验的话，配到别人家规格上的行永远命不中。
         *
         * 🔴 这一条必须排在「不低于挂牌价」【之前】。
         *    倒过来的话，把别人家的规格填进来时，报出来的是
         *    「覆盖价 888 不低于挂牌价 100」—— 而那个 100 是【另一件商品】的规格价，
         *    运营对着自己这件 100000 分的商品怎么看都想不通。联调时就是这么发现的。
         *    不知道是哪个规格，就没资格谈挂牌价。
         */
        if (form.getSkuId() != WHOLE_COMMODITY) {
            MallSku sku = mallSkuManager.getById(form.getSkuId());
            if (sku == null || !form.getCommodityId().equals(sku.getCommodityId())) {
                throw new BusinessException("这个规格不属于这件商品");
            }
        }

        int listPrice = listPriceOf(form, commodity);
        if (form.getPointsPrice() >= listPrice) {
            /*
             * ④ 覆盖价不低于挂牌价 = 高等级反而更贵（或者白配一行）。
             *    MallPricing 那边取了 min 兜底，所以它不会真的加价，
             *    但那行配置就成了一个什么都不做的摆设 —— 而运营以为它在起作用。
             */
            throw new BusinessException(String.format(
                    "覆盖价 %d 不低于挂牌价 %d —— 高等级不该更贵。要涨价请改商品价",
                    form.getPointsPrice(), listPrice));
        }
        return commodity;
    }

    /** 这一行对着的挂牌价：规格行看规格价，商品行看商品基准价 */
    private int listPriceOf(MallGradePrice form, MallCommodity commodity) {
        if (form.getSkuId() == null || form.getSkuId() == WHOLE_COMMODITY) {
            return MallPricing.listPoints(commodity);
        }
        MallSku sku = mallSkuManager.getById(form.getSkuId());
        return sku == null ? MallPricing.listPoints(commodity) : MallPricing.listPoints(sku, commodity);
    }
}
