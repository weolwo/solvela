package solvela.ledger.coupon.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.dao.SolvelaPageUtil;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.domain.dto.MemberCouponDTO;
import solvela.ledger.coupon.domain.query.MemberCouponQuery;
import solvela.ledger.coupon.domain.dto.MemberCouponStatDTO;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import java.util.ArrayList;
import java.util.List;

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
        StatRow issued = StatRow.of(memberCouponDao.selectIssuedStat(form));
        StatRow stock = StatRow.of(memberCouponDao.selectStockStat());
        long issuedCount = issued.count("issuedCount");

        MemberCouponStatDTO vo = new MemberCouponStatDTO();
        fillIssued(issued, vo);
        fillUsed(StatRow.of(memberCouponDao.selectUsedStat(form)), vo);
        fillStock(stock, vo);
        vo.setCouponList(couponStats(form));
        vo.setSourceList(sourceStats(form, issuedCount));
        vo.setIssueList(checkup(stock));
        return vo;
    }

    /** 本期发放：这批券里已经有多少张被用掉了 */
    private void fillIssued(StatRow issued, MemberCouponStatDTO vo) {
        long issuedCount = issued.count("issuedCount");
        long issuedUsed = issued.count("issuedUsedCount");
        vo.setIssuedCount(issuedCount);
        vo.setIssuedMemberCount(issued.count("issuedMemberCount"));
        vo.setIssuedUsedCount(issuedUsed);
        vo.setIssuedUsedRate(Rate.share(issuedUsed, issuedCount));
    }

    /**
     * 本期核销。
     *
     * <p>⚠️ 走的是 {@code used_time} 这一列，和上面那批<b>不是同一批券</b>：
     * 今天核销的多半是前几周发的。两个数不要相减，差值没有任何含义。
     */
    private void fillUsed(StatRow used, MemberCouponStatDTO vo) {
        vo.setUsedCount(used.count("usedCount"));
        vo.setUsedMemberCount(used.count("usedMemberCount"));
    }

    /** 券库存：全量，不受时间范围影响 */
    private void fillStock(StatRow stock, MemberCouponStatDTO vo) {
        vo.setStockTotalCount(stock.count("stockTotalCount"));
        vo.setStockUnusedCount(stock.count("stockUnusedCount"));
        vo.setStockUsedCount(stock.count("stockUsedCount"));
        vo.setStockExpiredCount(stock.count("stockExpiredCount"));
        vo.setStockVoidCount(stock.count("stockVoidCount"));
        vo.setStaleUnusedCount(stock.count("staleUnusedCount"));
        vo.setExpiringSoonCount(stock.count("expiringSoonCount"));
    }

    /** 券模分布：哪个券发得多、哪个券没人用 */
    private List<MemberCouponStatDTO.CouponStatDTO> couponStats(LedgerStatQuery form) {
        List<MemberCouponStatDTO.CouponStatDTO> couponList = new ArrayList<>();
        for (StatRow row : StatRow.of(memberCouponDao.selectCouponStat(form))) {
            long count = row.count("issuedCount");
            long used = row.count("usedCount");

            MemberCouponStatDTO.CouponStatDTO item = new MemberCouponStatDTO.CouponStatDTO();
            item.setCouponCode(row.text("couponCode"));
            item.setCouponName(row.text("couponName"));
            item.setIssuedCount(count);
            item.setMemberCount(row.count("memberCount"));
            item.setUsedCount(used);
            item.setUsedRate(Rate.share(used, count));
            item.setStaleCount(row.count("staleCount"));
            couponList.add(item);
        }
        return couponList;
    }

    /**
     * 来源分布：券是从哪来的。
     *
     * @param issuedTotal 本期发放总数，用作占比分母
     */
    private List<MemberCouponStatDTO.SourceStatDTO> sourceStats(LedgerStatQuery form, long issuedTotal) {
        List<MemberCouponStatDTO.SourceStatDTO> sourceList = new ArrayList<>();
        for (StatRow row : StatRow.of(memberCouponDao.selectSourceStat(form))) {
            long count = row.count("issuedCount");

            MemberCouponStatDTO.SourceStatDTO item = new MemberCouponStatDTO.SourceStatDTO();
            item.setSourceType(row.text("sourceType"));
            item.setIssuedCount(count);
            item.setIssuedShare(Rate.share(count, issuedTotal));
            sourceList.add(item);
        }
        return sourceList;
    }

    /**
     * 券库存体检。全部对着存量那一份数据查，与时间范围无关。
     */
    private List<String> checkup(StatRow stock) {
        long unused = stock.count("stockUnusedCount");
        long stale = stock.count("staleUnusedCount");
        return new Checkup()
                .countIf(stale,
                        "有 {} 张券已过有效期却仍是「未使用」：全工程没有任何地方把券置为 2-已过期"
                                + "（既没有定时任务，也没有任何代码写这个状态），它们不会自己收口 —— "
                                + "用户端会一直看到一张永远用不了的券，而上面的「未使用 {} 张」也因此是虚高的，"
                                + "真正还能用的是 {} 张",
                        unused, unused - stale)
                .countIf(stock.count("expiringSoonCount"),
                        "有 {} 张券将在 7 天内到期且还没被使用：现在催还来得及，"
                                + "过期之后就只能算一次失败的发放（而且不会有任何地方把它标成过期）")
                .countIf(stock.count("usedNoTimeCount"),
                        "有 {} 张券状态是「已使用」却没有核销时间：事后说不清是哪天用的，"
                                + "按核销时间做的统计也统计不到它们")
                .countIf(stock.count("unusedWithTimeCount"),
                        "有 {} 张券状态是「未使用」却带着核销时间：与状态自相矛盾，"
                                + "多半是被人工改回过状态")
                .countIf(stock.count("badValidRangeCount"),
                        "有 {} 张券的有效期结束时间不晚于开始时间：这种券发出去就是废的，"
                                + "用户拿到当场就用不了")
                .issues();
    }

}
