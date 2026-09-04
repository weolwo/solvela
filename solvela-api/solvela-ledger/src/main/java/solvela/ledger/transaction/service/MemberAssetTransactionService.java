package solvela.ledger.transaction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.dao.SolvelaPageUtil;
import solvela.ledger.stat.domain.query.LedgerStatQuery;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionDTO;
import solvela.ledger.transaction.domain.query.MemberAssetTransactionQuery;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionStatDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易明细表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberAssetTransactionService {

    private final MemberAssetTransactionDao memberAssetTransactionDao;

    /**
     * 分页查询
     */
    public PageResult<MemberAssetTransactionDTO> queryPage(MemberAssetTransactionQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberAssetTransactionDTO> list = memberAssetTransactionDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }


    /**
     * 交易统计：这段时间里钱是怎么进出的。
     *
     * <p>时间范围不传就是数据库当天（落在 SQL 的 {@code COALESCE(..., CURDATE())} 里）。
     * 本接口<b>刻意不吃页面上的列表筛选</b>：它要回答的是「整体发生了什么」，
     * 跟着某个会员名筛之后就不再是概览了。
     */
    public MemberAssetTransactionStatDTO stat(LedgerStatQuery form) {
        StatRow row = StatRow.of(memberAssetTransactionDao.selectStat(form));

        MemberAssetTransactionStatDTO vo = new MemberAssetTransactionStatDTO();
        vo.setTxCount(row.count("txCount"));
        vo.setMemberCount(row.count("memberCount"));
        vo.setManualAdjustCount(row.count("manualAdjustCount"));
        vo.setAssetList(assetFlows(form));
        vo.setBizTypeList(bizTypeStats(form, row.count("txCount")));
        vo.setIssueList(checkup(row));
        return vo;
    }

    /**
     * 按资产类型的收支。<b>金额只在同一 assetType 内可加</b>，所以这里一行一种资产，
     * 页面上也没有合计列 —— 积分和现金加起来的那个数没有任何意义。
     */
    public List<MemberAssetTransactionStatDTO.AssetFlowDTO> assetFlows(LedgerStatQuery form) {
        List<MemberAssetTransactionStatDTO.AssetFlowDTO> assetList = new ArrayList<>();
        for (StatRow stat : StatRow.of(memberAssetTransactionDao.selectAssetFlowStat(form))) {
            assetList.add(toAssetFlow(stat));
        }
        return assetList;
    }

    /**
     * 业务类型分布：<b>只给笔数</b>。跨业务类型的金额同样不可加 ——
     * 「发奖 100 积分」与「消费 100 元」放进同一列，得到的是一个假的合计。
     *
     * @param txTotal 交易总笔数，用作占比分母
     */
    private List<MemberAssetTransactionStatDTO.BizTypeStatDTO> bizTypeStats(LedgerStatQuery form, long txTotal) {
        List<MemberAssetTransactionStatDTO.BizTypeStatDTO> bizTypeList = new ArrayList<>();
        for (StatRow stat : StatRow.of(memberAssetTransactionDao.selectBizTypeStat(form))) {
            long count = stat.count("txCount");

            MemberAssetTransactionStatDTO.BizTypeStatDTO item = new MemberAssetTransactionStatDTO.BizTypeStatDTO();
            item.setBizType(stat.text("bizType"));
            item.setTxCount(count);
            item.setMemberCount(stat.count("memberCount"));
            item.setTxShare(Rate.share(count, txTotal));
            bizTypeList.add(item);
        }
        return bizTypeList;
    }

    /**
     * 流水体检：三条都是「余额对不上账」时第一时间要看的东西。
     */
    private List<String> checkup(StatRow row) {
        return new Checkup()
                .countIf(row.count("negativeBalanceCount"),
                        "有 {} 笔流水的「变动后余额」是负数：扣减走的是 deductBalanceWithVersion"
                                + "（条件里带 balance >= amount），正常扣不出负数 —— 出现说明有人绕过钱包服务直接改了库，"
                                + "或者这几行流水是手工插的。用户余额对不上账就从这里查")
                .countIf(row.count("nonPositiveChangeCount"),
                        "有 {} 笔流水的变动金额不是正数：change_amount 这一列存的是"
                                + "「变动绝对值」，方向由 transaction_type 单独表示，恒该大于 0。"
                                + "写入侧把负号写进数值里的话，上面的收入/支出两栏会同时算错")
                .countIf(row.count("manualAdjustCount"),
                        "这段时间有 {} 笔人工调账（biz_type = MANUAL_ADJUST）："
                                + "系统自己发的奖不需要人插手，这一类是有人手工改了别人的钱，"
                                + "量再小也建议逐笔确认一下操作人和事由")
                .issues();
    }

    /**
     * 把一行「按资产类型的收支」转成 VO。钱包页面的「今日变动」复用同一份 SQL，
     * 也复用这个转换 —— 两处各转一次，早晚会在某个字段上漂。
     */
    private static MemberAssetTransactionStatDTO.AssetFlowDTO toAssetFlow(StatRow stat) {
        BigDecimal income = stat.amount("incomeAmount");
        BigDecimal expense = stat.amount("expenseAmount");

        MemberAssetTransactionStatDTO.AssetFlowDTO item = new MemberAssetTransactionStatDTO.AssetFlowDTO();
        item.setAssetType(stat.text("assetType"));
        item.setIncomeCount(stat.count("incomeCount"));
        item.setIncomeAmount(income);
        item.setExpenseCount(stat.count("expenseCount"));
        item.setExpenseAmount(expense);
        // 净额可能为负：这段时间用户手上的这种资产是净减少的，负号本身就是结论，不要取绝对值
        item.setNetAmount(income.subtract(expense));
        return item;
    }

}
