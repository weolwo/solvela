package net.lab1024.sa.ledger.wallet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.code.BizErrorCode;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.transaction.dao.MemberAssetTransactionDao;
import net.lab1024.sa.ledger.transaction.domain.entity.MemberAssetTransaction;
import net.lab1024.sa.ledger.wallet.dao.MemberWalletDao;
import net.lab1024.sa.ledger.wallet.domain.entity.MemberWallet;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletAddForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletUpdateForm;
import net.lab1024.sa.ledger.wallet.domain.vo.MemberWalletVO;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
     * 分页查询
     */
    public PageResult<MemberWalletVO> queryPage(MemberWalletQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberWalletVO> list = memberWalletDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MemberWalletAddForm addForm) {
        MemberWallet memberWallet = SmartBeanUtil.copy(addForm, MemberWallet.class);
        memberWalletDao.insert(memberWallet);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    public ResponseDTO<String> update(MemberWalletUpdateForm updateForm) {
        MemberWallet memberWallet = SmartBeanUtil.copy(updateForm, MemberWallet.class);
        memberWalletDao.updateById(memberWallet);
        return ResponseDTO.ok();
    }

    /**
     * 【资深写法】返回 void，失败直接抛出 BizException，让外层去捕获并转为 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeWalletCharge(ProposalRecord proposal) {
        BigDecimal amount = proposal.getPromotionValue();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }

        // 1. 查钱包与自愈
        MemberWallet wallet = memberWalletDao.getMemberByMemberName(proposal.getMemberName());
        if (wallet == null) {
            wallet = initMemberWallet(proposal.getMemberName(), proposal.getTenantId());
        }

        // 2. 状态校验 (调用充血模型)
        wallet.checkAvailable();
        BigDecimal balanceAfter = wallet.calculateAfterBalance(amount);

        // 3. 乐观锁更新
        int updateRows = memberWalletDao.addCashBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());
        if (updateRows == 0) {
            // 抛出专用的并发异常，外层可以根据这个做特定处理
            throw new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED);
        }

        // 4. 写流水 (省略构建过程，直接看核心)
        MemberAssetTransaction txn = buildTransaction(proposal, amount, balanceAfter);
        memberAssetTransactionDao.insert(txn);
    }

    private MemberAssetTransaction buildTransaction(ProposalRecord proposal, BigDecimal amount, BigDecimal balanceAfter) {
        MemberAssetTransaction txn = new MemberAssetTransaction();
        txn.setTenantId(proposal.getTenantId());
        txn.setMemberName(proposal.getMemberName());
        txn.setAssetType(PrizeTypeEnum.BALANCE.name());
        txn.setTransactionType(1); // 1-收入
        txn.setChangeAmount(amount);
        txn.setBalanceAfter(balanceAfter); // 留下不可磨灭的财务对账证据
        txn.setBizType("PROPOSAL_REWARD"); // 业务来源分类
        txn.setBizRefId(proposal.getId().toString()); // 极度关键：溯源单号
        txn.setRemark(proposal.getRemark() != null ? proposal.getRemark() : "营销活动余额派发");
        return txn;
    }


    /**
     * 辅助方法：用户钱包初始化兜底
     */
    private MemberWallet initMemberWallet(String memberName, String tenantId) {
        MemberWallet wallet = new MemberWallet();
        wallet.setTenantId(tenantId);
        wallet.setMemberName(memberName);
        wallet.setScoreBalance(BigDecimal.ZERO);
        wallet.setCashBalance(BigDecimal.ZERO);
        wallet.setStatus(1); // 1-正常
        wallet.setVersion(0);

        try {
            memberWalletDao.insert(wallet);
        } catch (DuplicateKeyException e) {
            // 防止高并发下同时初始化，如果报冲突，重新查一次即可
            wallet = memberWalletDao.getMemberByMemberName(memberName);
        }
        return wallet;
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        memberWalletDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        memberWalletDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
