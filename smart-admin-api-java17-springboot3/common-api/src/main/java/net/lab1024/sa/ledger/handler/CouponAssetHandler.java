package net.lab1024.sa.ledger.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.coupon.dao.MemberCouponDao;
import net.lab1024.sa.ledger.coupon.domain.entity.MemberCoupon;
import net.lab1024.sa.prize.prizelog.dao.PrizeLogDao;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.COUPON)
public class CouponAssetHandler implements IAssetHandler{
    @Resource
    private MemberCouponDao memberCouponDao;

    @Resource
    private PrizeLogDao prizeLogDao;

    /**
     * 券类型默认值。
     * TODO 产品规则待定：t_prize_config 没有券类型字段，定了之后应从奖品配置（或其 ext JSON）读取
     */
    private static final String DEFAULT_COUPON_TYPE = "GENERAL";

    /**
     * 券有效期默认天数。
     * TODO 产品规则待定：UserPrizeEvent.validUntil 和 t_prize_log.valid_until 都预留了字段却从没赋过值，
     * 等确定是「按固定天数」还是「按活动结束时间」后改为读配置
     */
    private static final int DEFAULT_VALID_DAYS = 30;

    private static final String SOURCE_TYPE_PROPOSAL = "PROPOSAL";

    @Override
    public ResponseDTO dispatch(ProposalRecord proposal) {
        // 回查发奖流水补齐券模信息：ProposalRecord 里只有 source_biz_id、没有 prize_code，
        // 而 t_prize_log.external_biz_no 存的正是同一个业务单号，据此才知道该发哪种券
        PrizeLog prizeLog = prizeLogDao.selectOne(Wrappers.<PrizeLog>lambdaQuery()
                .eq(PrizeLog::getExternalBizNo, proposal.getSourceBizId())
                .last("limit 1"));

        MemberCoupon coupon = new MemberCoupon();
        coupon.setTenantId(proposal.getTenantId());
        coupon.setMemberName(proposal.getMemberName());

        // 下面四项都是 NOT NULL 且无默认值的列：漏任意一个，MySQL 在严格模式下会以
        // 「Field 'xxx' doesn't have a default value」整条拒绝；又因为本方法运行在提案事务内，
        // 一次插入失败会把提案记录一起回滚掉，连失败痕迹都留不下（压测时就是这么丢的）
        coupon.setCouponCode(prizeLog == null ? proposal.getSourceBizId() : prizeLog.getPrizeCode());
        coupon.setCouponType(DEFAULT_COUPON_TYPE);
        coupon.setCouponName(prizeLog == null ? proposal.getRemark() : prizeLog.getPrizeName());
        LocalDateTime now = LocalDateTime.now();
        coupon.setValidStartTime(now);
        coupon.setValidEndTime(now.plusDays(DEFAULT_VALID_DAYS));

        coupon.setSourceType(SOURCE_TYPE_PROPOSAL); // 来源是提案
        coupon.setSourceBizId(proposal.getId().toString()); // 溯源提案ID
        coupon.setStatus(0); // 0-未使用

        try {
            memberCouponDao.insert(coupon);
            log.info(">>>> [发券成功] 提案ID: {}, 券模: {}", proposal.getId(), coupon.getCouponCode());
            return ResponseDTO.ok();
        } catch (DuplicateKeyException e) {
            log.warn("【防重拦截】该提案已发过券: {}", proposal.getId());
            return ResponseDTO.ok(); // 幂等，视为成功
        }
    }
}
