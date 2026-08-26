package solvela.ledger.coupon.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.domain.dto.MemberCouponDTO;
import solvela.ledger.coupon.domain.query.MemberCouponQuery;
import solvela.ledger.coupon.domain.dto.MemberCouponStatDTO;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static solvela.ledger.stat.LedgerStatSupport.*;

/**
 * 会员优惠券 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberCouponService {

    private final MemberCouponDao memberCouponDao;

    /**
     * 分页查询
     */
    public PageResult<MemberCouponDTO> queryPage(MemberCouponQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberCouponDTO> list = memberCouponDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }


    /**
     * 优惠券统计：本期发了多少、本期核销了多少，以及手上还压着多少张。
     *
     * <p><b>发放和核销是两个口径，走两个不同的时间列</b>（{@code create_time} / {@code used_time}）：
     * 用同一个窗口算「今日核销 / 今日发放」得到的必然是个接近 0 的数，
     * 今天刚发的券当然还没来得及用 —— 那不是核销率低，是口径错了。
     *
     * <p>券库存那一组<b>刻意不带时间窗</b>：压着多少张没用的券是存量问题，
     * 限制在今天只会把它藏起来。
     */
    public MemberCouponStatDTO stat(LedgerStatQuery form) {
        MemberCouponStatDTO vo = new MemberCouponStatDTO();

        // ---- 本期发放 ----
        Map<String, Object> issued = memberCouponDao.selectIssuedStat(form);
        long issuedCount = toLong(issued.get("issuedCount"));
        long issuedUsed = toLong(issued.get("issuedUsedCount"));
        vo.setIssuedCount(issuedCount);
        vo.setIssuedMemberCount(toLong(issued.get("issuedMemberCount")));
        vo.setIssuedUsedCount(issuedUsed);
        vo.setIssuedUsedRate(rate(issuedUsed, issuedCount));

        // ---- 本期核销（另一个时间列，和上面不是同一批券，两个数不要相减）----
        Map<String, Object> used = memberCouponDao.selectUsedStat(form);
        vo.setUsedCount(toLong(used.get("usedCount")));
        vo.setUsedMemberCount(toLong(used.get("usedMemberCount")));

        // ---- 券库存：全量，不受时间范围影响 ----
        Map<String, Object> stock = memberCouponDao.selectStockStat();
        long unused = toLong(stock.get("stockUnusedCount"));
        long stale = toLong(stock.get("staleUnusedCount"));
        vo.setStockTotalCount(toLong(stock.get("stockTotalCount")));
        vo.setStockUnusedCount(unused);
        vo.setStockUsedCount(toLong(stock.get("stockUsedCount")));
        vo.setStockExpiredCount(toLong(stock.get("stockExpiredCount")));
        vo.setStockVoidCount(toLong(stock.get("stockVoidCount")));
        vo.setStaleUnusedCount(stale);
        vo.setExpiringSoonCount(toLong(stock.get("expiringSoonCount")));

        // ---- 券模分布 ----
        List<MemberCouponStatDTO.CouponStatDTO> couponList = new ArrayList<>();
        for (Map<String, Object> row : memberCouponDao.selectCouponStat(form)) {
            MemberCouponStatDTO.CouponStatDTO item = new MemberCouponStatDTO.CouponStatDTO();
            long count = toLong(row.get("issuedCount"));
            long usedInBatch = toLong(row.get("usedCount"));
            item.setCouponCode(toStr(row, "couponCode"));
            item.setCouponName(toStr(row, "couponName"));
            item.setIssuedCount(count);
            item.setMemberCount(toLong(row.get("memberCount")));
            item.setUsedCount(usedInBatch);
            item.setUsedRate(rate(usedInBatch, count));
            item.setStaleCount(toLong(row.get("staleCount")));
            couponList.add(item);
        }
        vo.setCouponList(couponList);

        // ---- 来源分布 ----
        List<MemberCouponStatDTO.SourceStatDTO> sourceList = new ArrayList<>();
        for (Map<String, Object> row : memberCouponDao.selectSourceStat(form)) {
            MemberCouponStatDTO.SourceStatDTO item = new MemberCouponStatDTO.SourceStatDTO();
            long count = toLong(row.get("issuedCount"));
            item.setSourceType(toStr(row, "sourceType"));
            item.setIssuedCount(count);
            item.setIssuedShare(rate(count, issuedCount));
            sourceList.add(item);
        }
        vo.setSourceList(sourceList);

        // ---- 体检 ----
        List<String> issues = new ArrayList<>();
        if (stale > 0) {
            issues.add("有 " + stale + " 张券已过有效期却仍是「未使用」：全工程没有任何地方把券置为 2-已过期"
                    + "（既没有定时任务，也没有任何代码写这个状态），它们不会自己收口 —— "
                    + "用户端会一直看到一张永远用不了的券，而上面的「未使用 " + unused + " 张」也因此是虚高的，"
                    + "真正还能用的是 " + (unused - stale) + " 张");
        }
        long expiringSoon = toLong(stock.get("expiringSoonCount"));
        if (expiringSoon > 0) {
            issues.add("有 " + expiringSoon + " 张券将在 7 天内到期且还没被使用：现在催还来得及，"
                    + "过期之后就只能算一次失败的发放（而且不会有任何地方把它标成过期）");
        }
        long usedNoTime = toLong(stock.get("usedNoTimeCount"));
        if (usedNoTime > 0) {
            issues.add("有 " + usedNoTime + " 张券状态是「已使用」却没有核销时间：事后说不清是哪天用的，"
                    + "按核销时间做的统计也统计不到它们");
        }
        long unusedWithTime = toLong(stock.get("unusedWithTimeCount"));
        if (unusedWithTime > 0) {
            issues.add("有 " + unusedWithTime + " 张券状态是「未使用」却带着核销时间：与状态自相矛盾，"
                    + "多半是被人工改回过状态");
        }
        long badRange = toLong(stock.get("badValidRangeCount"));
        if (badRange > 0) {
            issues.add("有 " + badRange + " 张券的有效期结束时间不晚于开始时间：这种券发出去就是废的，"
                    + "用户拿到当场就用不了");
        }
        vo.setIssueList(issues);
        return vo;
    }

}
