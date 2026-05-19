package net.lab1024.sa.ledger.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.coupon.dao.MemberCouponDao;
import net.lab1024.sa.ledger.coupon.domain.entity.MemberCoupon;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.COUPON)
public class CouponAssetHandler implements IAssetHandler{
    @Resource
    private MemberCouponDao memberCouponDao;

    @Override
    public boolean dispatch(ProposalRecord proposal) {
        // 你的 DDL 设计得极好：source_type 和 source_biz_id 完美溯源
        MemberCoupon coupon = new MemberCoupon();
        coupon.setTenantId(proposal.getTenantId());
        coupon.setMemberName(proposal.getMemberName());

        coupon.setCouponCode("");
        coupon.setCouponName(proposal.getRemark());
        // ... (省略有效期计算等设值)

        coupon.setSourceType("PROPOSAL"); // 来源是提案
        coupon.setSourceBizId(proposal.getId().toString()); // 溯源提案ID
        coupon.setStatus(0); // 0-未使用

        try {
            memberCouponDao.insert(coupon);
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("【防重拦截】该提案已发过券: {}", proposal.getId());
            return true; // 幂等，视为成功
        }
    }
}
