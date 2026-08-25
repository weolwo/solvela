package solvela.ledger.transaction.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.common.domain.PageResult;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.transaction.domain.form.MemberAssetTransactionQueryForm;
import solvela.ledger.transaction.domain.vo.MemberAssetTransactionStatVO;
import solvela.ledger.transaction.domain.vo.MemberAssetTransactionVO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static solvela.ledger.stat.LedgerStatSupport.*;

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
    public PageResult<MemberAssetTransactionVO> queryPage(MemberAssetTransactionQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberAssetTransactionVO> list = memberAssetTransactionDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }


    /**
     * 交易统计：这段时间里钱是怎么进出的。
     *
     * <p>时间范围不传就是数据库当天（落在 SQL 的 {@code COALESCE(..., CURDATE())} 里）。
     * 本接口<b>刻意不吃页面上的列表筛选</b>：它要回答的是「整体发生了什么」，
     * 跟着某个会员名筛之后就不再是概览了。
     */
    public MemberAssetTransactionStatVO stat(LedgerStatForm form) {
        Map<String, Object> row = memberAssetTransactionDao.selectStat(form);
        MemberAssetTransactionStatVO vo = new MemberAssetTransactionStatVO();

        long txCount = toLong(row.get("txCount"));
        long manualAdjust = toLong(row.get("manualAdjustCount"));
        vo.setTxCount(txCount);
        vo.setMemberCount(toLong(row.get("memberCount")));
        vo.setManualAdjustCount(manualAdjust);

        // ---- 按资产类型的收支：金额只在同一 assetType 内可加 ----
        List<MemberAssetTransactionStatVO.AssetFlowVO> assetList = new ArrayList<>();
        for (Map<String, Object> stat : memberAssetTransactionDao.selectAssetFlowStat(form)) {
            assetList.add(toAssetFlow(stat));
        }
        vo.setAssetList(assetList);

        // ---- 业务类型分布：只给笔数 ----
        List<MemberAssetTransactionStatVO.BizTypeStatVO> bizTypeList = new ArrayList<>();
        for (Map<String, Object> stat : memberAssetTransactionDao.selectBizTypeStat(form)) {
            MemberAssetTransactionStatVO.BizTypeStatVO item = new MemberAssetTransactionStatVO.BizTypeStatVO();
            long count = toLong(stat.get("txCount"));
            item.setBizType(toStr(stat, "bizType"));
            item.setTxCount(count);
            item.setMemberCount(toLong(stat.get("memberCount")));
            item.setTxShare(rate(count, txCount));
            bizTypeList.add(item);
        }
        vo.setBizTypeList(bizTypeList);

        // ---- 体检 ----
        List<String> issues = new ArrayList<>();
        long negativeBalance = toLong(row.get("negativeBalanceCount"));
        if (negativeBalance > 0) {
            issues.add("有 " + negativeBalance + " 笔流水的「变动后余额」是负数：扣减走的是 deductBalanceWithVersion"
                    + "（条件里带 balance >= amount），正常扣不出负数 —— 出现说明有人绕过钱包服务直接改了库，"
                    + "或者这几行流水是手工插的。用户余额对不上账就从这里查");
        }
        long nonPositiveChange = toLong(row.get("nonPositiveChangeCount"));
        if (nonPositiveChange > 0) {
            issues.add("有 " + nonPositiveChange + " 笔流水的变动金额不是正数：change_amount 这一列存的是"
                    + "「变动绝对值」，方向由 transaction_type 单独表示，恒该大于 0。"
                    + "写入侧把负号写进数值里的话，上面的收入/支出两栏会同时算错");
        }
        if (manualAdjust > 0) {
            issues.add("这段时间有 " + manualAdjust + " 笔人工调账（biz_type = MANUAL_ADJUST）："
                    + "系统自己发的奖不需要人插手，这一类是有人手工改了别人的钱，"
                    + "量再小也建议逐笔确认一下操作人和事由");
        }
        vo.setIssueList(issues);
        return vo;
    }

    /**
     * 把一行「按资产类型的收支」转成 VO。钱包页面的「今日变动」复用同一份 SQL，
     * 也复用这个转换 —— 两处各转一次，早晚会在某个字段上漂。
     */
    public static MemberAssetTransactionStatVO.AssetFlowVO toAssetFlow(Map<String, Object> stat) {
        MemberAssetTransactionStatVO.AssetFlowVO item = new MemberAssetTransactionStatVO.AssetFlowVO();
        BigDecimal income = toDecimal(stat.get("incomeAmount"));
        BigDecimal expense = toDecimal(stat.get("expenseAmount"));
        item.setAssetType(toStr(stat, "assetType"));
        item.setIncomeCount(toLong(stat.get("incomeCount")));
        item.setIncomeAmount(income);
        item.setExpenseCount(toLong(stat.get("expenseCount")));
        item.setExpenseAmount(expense);
        // 净额可能为负：这段时间用户手上的这种资产是净减少的，负号本身就是结论，不要取绝对值
        item.setNetAmount(income.subtract(expense));
        return item;
    }

}
