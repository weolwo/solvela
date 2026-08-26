package solvela.ledger.wallet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.code.BizErrorCode;
import solvela.base.domain.PageResult;
import solvela.exception.BusinessException;
import solvela.base.dao.SolvelaPageUtil;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.stat.domain.form.LedgerStatForm;
import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.MemberAssetTransaction;
import solvela.ledger.transaction.domain.vo.MemberAssetTransactionStatVO;
import solvela.ledger.transaction.service.MemberAssetTransactionService;
import solvela.ledger.wallet.dao.MemberWalletDao;
import solvela.ledger.MemberWallet;
import solvela.ledger.wallet.domain.form.MemberWalletQueryForm;
import solvela.ledger.wallet.domain.vo.MemberWalletStatVO;
import solvela.ledger.wallet.domain.vo.MemberWalletVO;
import solvela.member.service.MemberService;
import solvela.risk.ProposalRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static solvela.ledger.stat.LedgerStatSupport.*;

/**
 * （只抛异常，绝不返回 DTO）
 * Service 回归纯粹的“资源协调者”身份
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MemberWalletService {

    private final MemberWalletDao memberWalletDao;
    private final MemberAssetTransactionDao memberAssetTransactionDao;
    /**
     * 只用来取流水上的<b>账号展示快照</b>。钱包本身只认 member_id，
     * 不需要名字；是流水（单据类）要把「当时那个账号」记下来。
     */
    private final MemberService memberService;

    /**
     * 钱包统计：用户手上现在有多少资产（存量，全量），以及这段时间变动了多少（跟时间范围走）。
     *
     * <p><b>余额刻意不跟时间范围走</b>：钱包表是存量表，一个会员一种资产一行、只有当前余额，
     * 没有历史切片。按 create_time 筛出来的是「今天新开的钱包」，那几个账户的余额加起来
     * 既不是今天发出去的钱、也不是用户现在手上的钱 —— 是一个什么都不回答的数字。
     *
     * <p>「本期变动」直接调交易明细页的那条 SQL（{@code selectAssetFlowStat}），
     * 不在钱包这边另写一份：同一个口径两处实现，早晚会漂成两个对不上的数。
     */
    public MemberWalletStatVO stat(LedgerStatForm form) {
        MemberWalletStatVO vo = new MemberWalletStatVO();

        Map<String, Object> row = memberWalletDao.selectStat();
        vo.setWalletCount(toLong(row.get("walletCount")));
        vo.setMemberCount(toLong(row.get("memberCount")));
        vo.setFrozenCount(toLong(row.get("frozenCount")));

        // ---- 资产存量：余额只在同一资产类型内可加 ----
        List<MemberWalletStatVO.AssetBalanceVO> assetList = new ArrayList<>();
        for (Map<String, Object> stat : memberWalletDao.selectAssetBalanceStat()) {
            MemberWalletStatVO.AssetBalanceVO item = new MemberWalletStatVO.AssetBalanceVO();
            long walletCount = toLong(stat.get("walletCount"));
            BigDecimal totalBalance = toDecimal(stat.get("totalBalance"));
            item.setAssetType(toStr(stat, "assetType"));
            item.setWalletCount(walletCount);
            item.setTotalBalance(totalBalance);
            item.setAvgBalance(walletCount == 0 ? BigDecimal.ZERO
                    : totalBalance.divide(BigDecimal.valueOf(walletCount), 2, RoundingMode.HALF_UP));
            item.setFrozenBalance(toDecimal(stat.get("frozenBalance")));
            assetList.add(item);
        }
        vo.setAssetList(assetList);

        // ---- 本期变动：复用交易明细页的 SQL 与转换 ----
        List<MemberAssetTransactionStatVO.AssetFlowVO> flowList = new ArrayList<>();
        for (Map<String, Object> stat : memberAssetTransactionDao.selectAssetFlowStat(form)) {
            flowList.add(MemberAssetTransactionService.toAssetFlow(stat));
        }
        vo.setFlowList(flowList);

        // ---- 体检 ----
        List<String> issues = new ArrayList<>();
        long negativeBalance = toLong(row.get("negativeBalanceCount"));
        if (negativeBalance > 0) {
            issues.add("有 " + negativeBalance + " 个钱包余额是负数：扣减走的是 deductBalanceWithVersion"
                    + "（条件里带 balance >= amount），正常扣不出负数 —— 出现说明有人绕过钱包服务直接改了库");
        }
        long frozenWithBalance = toLong(row.get("frozenWithBalanceCount"));
        if (frozenWithBalance > 0) {
            issues.add("有 " + frozenWithBalance + " 个账户被冻结但里面还有余额：钱在账上，"
                    + "用户既取不出也用不了，冻结久了就是客诉 —— 这个数没人主动查就一直不会有人发现");
        }
        vo.setIssueList(issues);
        return vo;
    }

    /**
     * 分页查询
     */
    public PageResult<MemberWalletVO> queryPage(MemberWalletQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberWalletVO> list = memberWalletDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 【资深写法】返回 void，失败直接抛出 BizException，让外层去捕获并转为 DTO
     * 钱包一行一种资产：动哪种资产由调用方(各 AssetHandler)传入，新增资产类型无需改本方法
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeWalletCharge(ProposalRecord proposal, PrizeTypeEnum assetType) {
        BigDecimal amount = proposal.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }

        // 1. 查钱包与自愈
        MemberWallet wallet = memberWalletDao.getByMemberIdAndAssetType(proposal.getMemberId(), assetType.name());
        if (wallet == null) {
            wallet = initMemberWallet(proposal.getMemberId(), assetType);
        }

        // 2. 状态校验 (调用充血模型)
        wallet.checkAvailable();
        BigDecimal balanceAfter = wallet.calculateAfterBalance(amount);

        // 3. 乐观锁更新
        int updateRows = memberWalletDao.addBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());
        if (updateRows == 0) {
            // 抛出专用的并发异常，外层可以根据这个做特定处理
            throw new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED);
        }

        // 4. 写流水 (省略构建过程，直接看核心)
        MemberAssetTransaction txn = buildTransaction(proposal, assetType, amount, balanceAfter);
        memberAssetTransactionDao.insert(txn);
    }

    /**
     * 通用扣减（抽奖门票/积分消耗等场景）：乐观锁 + 余额充足双重条件，失败抛业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeWalletDeduct(Long memberId, PrizeTypeEnum assetType, BigDecimal amount,
                                    String bizType, String bizRefId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }
        // 会员必须真实存在：关联键指向一个查不到的会员，等于凭空造出一笔无主流水，
        // 而且当场不报错。顺带把账号取回来做流水上的展示快照。
        String memberName = memberService.requireMemberName(memberId);
        MemberWallet wallet = memberWalletDao.getByMemberIdAndAssetType(memberId, assetType.name());
        if (wallet == null || wallet.getBalance() == null || wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(BizErrorCode.BALANCE_NOT_ENOUGH);
        }
        wallet.checkAvailable();

        int updateRows = memberWalletDao.deductBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());
        if (updateRows == 0) {
            // 并发冲突或余额刚好被消耗，统一提示重试
            throw new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED);
        }

        MemberAssetTransaction txn = new MemberAssetTransaction();
        txn.setMemberId(memberId);
        txn.setMemberName(memberName);
        txn.setAssetType(assetType.name());
        txn.setTransactionType(2); // 2-支出
        txn.setChangeAmount(amount);
        txn.setBalanceAfter(wallet.getBalance().subtract(amount));
        txn.setBizType(bizType);
        txn.setBizRefId(bizRefId);
        txn.setRemark(remark);
        memberAssetTransactionDao.insert(txn);
    }

    /**
     * 通用退还/入账（抽奖无货退门票等补偿场景）：钱包不存在时自愈初始化
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeWalletRefund(Long memberId, PrizeTypeEnum assetType, BigDecimal amount,
                                    String bizType, String bizRefId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }
        String memberName = memberService.requireMemberName(memberId);
        MemberWallet wallet = memberWalletDao.getByMemberIdAndAssetType(memberId, assetType.name());
        if (wallet == null) {
            wallet = initMemberWallet(memberId, assetType);
        }
        wallet.checkAvailable();
        BigDecimal balanceAfter = wallet.calculateAfterBalance(amount);

        int updateRows = memberWalletDao.addBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());
        if (updateRows == 0) {
            throw new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED);
        }

        MemberAssetTransaction txn = new MemberAssetTransaction();
        txn.setMemberId(memberId);
        txn.setMemberName(memberName);
        txn.setAssetType(assetType.name());
        txn.setTransactionType(1); // 1-收入
        txn.setChangeAmount(amount);
        txn.setBalanceAfter(balanceAfter);
        txn.setBizType(bizType);
        txn.setBizRefId(bizRefId);
        txn.setRemark(remark);
        memberAssetTransactionDao.insert(txn);
    }

    private MemberAssetTransaction buildTransaction(ProposalRecord proposal, PrizeTypeEnum assetType, BigDecimal amount, BigDecimal balanceAfter) {
        MemberAssetTransaction txn = new MemberAssetTransaction();
        txn.setMemberId(proposal.getMemberId());
        // 展示快照取提案上的那一份，不再查一次会员表：提案落库时已经把「当时那个账号」记下来了
        txn.setMemberName(proposal.getMemberName());
        txn.setAssetType(assetType.name());
        txn.setTransactionType(1); // 1-收入
        txn.setChangeAmount(amount);
        txn.setBalanceAfter(balanceAfter); // 留下不可磨灭的财务对账证据
        txn.setBizType("PROPOSAL_REWARD"); // 业务来源分类
        txn.setBizRefId(proposal.getId().toString()); // 极度关键：溯源单号
        txn.setRemark(proposal.getRemark() != null ? proposal.getRemark() : "营销活动余额派发");
        return txn;
    }


    /**
     * 辅助方法：用户钱包初始化兜底（按资产类型初始化对应账户行）
     */
    private MemberWallet initMemberWallet(Long memberId, PrizeTypeEnum assetType) {
        MemberWallet wallet = new MemberWallet();
        wallet.setMemberId(memberId);
        wallet.setAssetType(assetType.name());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setStatus(1); // 1-正常
        wallet.setVersion(0);

        try {
            memberWalletDao.insert(wallet);
        } catch (DuplicateKeyException e) {
            // 防止高并发下同时初始化，如果报冲突，重新查一次即可
            wallet = memberWalletDao.getByMemberIdAndAssetType(memberId, assetType.name());
        }
        return wallet;
    }

}
