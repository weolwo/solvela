package sa.ledger.wallet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sa.base.common.code.BizErrorCode;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.exception.BusinessException;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartPageUtil;
import sa.enums.PrizeTypeEnum;
import sa.ledger.transaction.dao.MemberAssetTransactionDao;
import sa.ledger.transaction.domain.entity.MemberAssetTransaction;
import sa.ledger.wallet.dao.MemberWalletDao;
import sa.ledger.wallet.domain.entity.MemberWallet;
import sa.ledger.wallet.domain.form.MemberWalletAddForm;
import sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import sa.ledger.wallet.domain.form.MemberWalletUpdateForm;
import sa.ledger.wallet.domain.vo.MemberWalletVO;
import sa.risk.proposal.domain.entity.ProposalRecord;
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
     * 钱包一行一种资产：动哪种资产由调用方(各 AssetHandler)传入，新增资产类型无需改本方法
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeWalletCharge(ProposalRecord proposal, PrizeTypeEnum assetType) {
        BigDecimal amount = proposal.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }

        // 1. 查钱包与自愈
        MemberWallet wallet = memberWalletDao.getByMemberNameAndAssetType(proposal.getMemberName(), assetType.name());
        if (wallet == null) {
            wallet = initMemberWallet(proposal.getMemberName(), proposal.getTenantId(), assetType);
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
    public void executeWalletDeduct(String memberName, PrizeTypeEnum assetType, BigDecimal amount,
                                    String bizType, String bizRefId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }
        MemberWallet wallet = memberWalletDao.getByMemberNameAndAssetType(memberName, assetType.name());
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
        txn.setTenantId(wallet.getTenantId());
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
    public void executeWalletRefund(String memberName, PrizeTypeEnum assetType, BigDecimal amount,
                                    String bizType, String bizRefId, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizErrorCode.AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        }
        MemberWallet wallet = memberWalletDao.getByMemberNameAndAssetType(memberName, assetType.name());
        if (wallet == null) {
            wallet = initMemberWallet(memberName, null, assetType);
        }
        wallet.checkAvailable();
        BigDecimal balanceAfter = wallet.calculateAfterBalance(amount);

        int updateRows = memberWalletDao.addBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());
        if (updateRows == 0) {
            throw new BusinessException(BizErrorCode.ACCOUNT_BALANCE_CHANGED);
        }

        MemberAssetTransaction txn = new MemberAssetTransaction();
        txn.setTenantId(wallet.getTenantId());
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
        txn.setTenantId(proposal.getTenantId());
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
    private MemberWallet initMemberWallet(String memberName, String tenantId, PrizeTypeEnum assetType) {
        MemberWallet wallet = new MemberWallet();
        wallet.setTenantId(tenantId);
        wallet.setMemberName(memberName);
        wallet.setAssetType(assetType.name());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setStatus(1); // 1-正常
        wallet.setVersion(0);

        try {
            memberWalletDao.insert(wallet);
        } catch (DuplicateKeyException e) {
            // 防止高并发下同时初始化，如果报冲突，重新查一次即可
            wallet = memberWalletDao.getByMemberNameAndAssetType(memberName, assetType.name());
        }
        return wallet;
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)) {
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
