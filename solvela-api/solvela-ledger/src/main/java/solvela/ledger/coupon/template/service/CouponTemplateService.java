package solvela.ledger.coupon.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.coupon.CouponTemplate;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.coupon.template.dao.CouponTemplateDao;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 券模板的维护。
 *
 * <h3>🔴 「保存」永远是新增一个版本，本类没有 update</h3>
 * 这不是漏了。用户手里那张「满100减20」，运营把模板改成「满200减20」之后，
 * 如果核销时读的是模板当前值，<b>用户手里的券就贬值了</b> ——
 * 那不是显示问题，是资损与信任问题。
 *
 * <p>所以规则只能往前加版本，而且发券时还要<b>快照</b>进
 * {@code t_member_coupon}，核销根本不读这张表。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponTemplateService {

    /** 折扣率的合法区间（1~99）。100 就是免单，那不该用券表达 */
    private static final BigDecimal PERCENT_MIN = BigDecimal.ONE;
    private static final BigDecimal PERCENT_MAX = BigDecimal.valueOf(99);

    private final CouponTemplateDao couponTemplateDao;

    /** 每个编码取最新启用版。列表页用 */
    public List<CouponTemplate> listLatest() {
        // selectAllEnabled 按 (code, version) 升序，反转后同 code 的新版在前，
        // 再按 code 去重拿到的就是每个 code 的最新版
        return couponTemplateDao.selectAllEnabled().reversed().stream()
                .filter(distinctByCode())
                .toList();
    }

    public List<CouponTemplate> listVersions(String couponCode) {
        return couponTemplateDao.selectVersions(couponCode);
    }

    public CouponTemplate getLatestEnabled(String couponCode) {
        return couponTemplateDao.selectLatestEnabled(couponCode);
    }

    /**
     * 保存 = <b>新增一个版本</b>。
     *
     * <p>版本号由服务端算「当前最大版本 + 1」，不让页面传 ——
     * 页面传的话两个人同时编辑就会撞版本号，而撞了的表现要么是一个
     * {@code DuplicateKeyException}，要么更糟：后保存的那份悄悄覆盖了前一份。
     *
     * @return 新版本号
     */
    public int save(CouponTemplate template) {
        validate(template);

        Integer maxVersion = couponTemplateDao.selectMaxVersion(template.getCouponCode());
        int next = maxVersion == null ? 1 : maxVersion + 1;
        template.setVersion(next);
        couponTemplateDao.insert(template);

        log.info("【券模板】{} 新增版本 v{}：{} {} {}",
                template.getCouponCode(), next, template.getCouponName(),
                template.getDiscountType(), template.getDiscountValue());
        return next;
    }

    /**
     * 停用某一版。
     *
     * <p>⚠️ <b>只停用不删除</b>。删掉一行，运营就再也回答不了
     * 「用户手里这张券当时是什么规则」—— 而券的纠纷恰恰总是要回答这个。
     * 所以本类没有 delete。
     */
    public void disable(String couponCode, Integer version) {
        // 🔴 不能用 updateById：复合主键，那个基类方法对本实体根本生成不出来。
        //    详见 CouponTemplateDao 上的类注释
        couponTemplateDao.disableVersion(couponCode, version);
    }

    /**
     * 保存前的校验。
     *
     * <p>🔴 这里每一条都对应一个「不校验就会变成资损」的场景，
     * 不是为了好看而加的必填。
     */
    private void validate(CouponTemplate template) {
        if (template.getCouponCode() == null || template.getCouponCode().isBlank()) {
            throw new BusinessException("券模编码不能为空");
        }
        if (template.getDiscountType() == null) {
            throw new BusinessException("抵扣方式不能为空");
        }
        if (template.getDiscountValue() == null || template.getDiscountValue().signum() <= 0) {
            throw new BusinessException("抵扣值必须大于 0");
        }
        if (template.getDeductTarget() == null) {
            throw new BusinessException("抵扣对象（现金/积分）不能为空");
        }

        if (template.getDiscountType() == CouponDiscountTypeEnum.PERCENT) {
            /*
             * 🔴 百分比券必须封顶。
             *
             * 不设上限的「8 折」碰上一台 7999 的 iPhone 就是减 1600 ——
             * 而它不报错、不告警，只是那一单少收了 1600 块。
             * 这是整个券模块里最容易造成资损、也最容易被忽略的一条，
             * 所以拦在保存这一步，而不是等核销时才发现。
             */
            if (template.getMaxDiscount() == null || template.getMaxDiscount().signum() <= 0) {
                throw new BusinessException("百分比券必须填「最高抵扣」—— 不封顶的话一单就能亏穿");
            }
            if (template.getDiscountValue().compareTo(PERCENT_MIN) < 0
                    || template.getDiscountValue().compareTo(PERCENT_MAX) > 0) {
                throw new BusinessException("折扣率要在 1~99 之间（20 表示减 20%）。100 是免单，不该用券表达");
            }
        }

        if (template.getMinAmount() == null) {
            template.setMinAmount(BigDecimal.ZERO);
        }
        if (template.getMinAmount().signum() < 0) {
            throw new BusinessException("最低消费门槛不能为负");
        }

        // 有效期两种表达二选一。都不填的话券永远不过期 —— 那是一个只增不减的负债
        if (template.getValidDays() == null && template.getValidEndTime() == null) {
            throw new BusinessException("有效期必须填一种：发券后 N 天，或固定失效时间");
        }
        if (template.getValidDays() != null && template.getValidEndTime() != null) {
            throw new BusinessException("有效期两种填法只能选一个，同时填会让「哪个说了算」变成一个没人记得的约定");
        }
        if (template.getValidDays() != null && template.getValidDays() <= 0) {
            throw new BusinessException("有效天数必须大于 0");
        }

        // 非 ALL 的范围必须给明细，否则那张券谁也用不了，而且不报错
        if (template.getScopeType() == null) {
            template.setScopeType(CouponScopeTypeEnum.ALL);
        }
        if (template.getScopeType() != CouponScopeTypeEnum.ALL
                && (template.getScopeRefs() == null || template.getScopeRefs().isBlank())) {
            throw new BusinessException("选了「" + template.getScopeType().getDesc() + "」就必须指定范围，否则这张券谁也用不了");
        }

        if (template.getStatus() == null) {
            template.setStatus(1);
        }
    }

    private static Predicate<CouponTemplate> distinctByCode() {
        Set<String> seen = new HashSet<>();
        return template -> seen.add(template.getCouponCode());
    }
}
