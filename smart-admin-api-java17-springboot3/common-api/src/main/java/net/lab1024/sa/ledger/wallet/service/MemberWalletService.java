package net.lab1024.sa.ledger.wallet.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
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
import java.util.Optional;

/**
 * 会员钱包表 Service
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

    @Transactional(rollbackFor = Exception.class) // 必须保证改余额和写流水的强一致性
    public ResponseDTO doDispatch(ProposalRecord proposal) {
        log.info(">>>> [底层账务引擎] 开始执行余额动账, 提案ID: {}, 会员: {}, 金额: {}",
                proposal.getId(), proposal.getMemberName(), proposal.getPromotionValue());

        BigDecimal amount = proposal.getPromotionValue();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("【账务风控拦截】动账金额非法(<=0), 提案ID: {}", proposal.getId());
            return ResponseDTO.userErrorParam("入账金额必须大于0");
        }

        try {
            // ==========================================
            // 1. 查询钱包状态 (附带容错自愈机制)
            // ==========================================
            MemberWallet wallet = memberWalletDao.getMemberByMemberName(proposal.getMemberName());
            if (wallet == null) {
                log.info("【账务系统自愈】用户钱包不存在，执行初始化，会员: {}", proposal.getMemberName());
                wallet = initMemberWallet(proposal.getMemberName(), proposal.getTenantId());
            }

            if (wallet.getStatus() != 1) { // 假设 status: 1-正常, 0-冻结
                log.error("【账务风控拦截】用户钱包已被冻结，拒绝入账！提案ID: {}", proposal.getId());
                return ResponseDTO.userErrorParam("用户账户异常，无法入账");
            }

            // 2. 内存计算期末余额 (用于写流水)
            BigDecimal balanceBefore = Optional.ofNullable(wallet.getCashBalance()).orElse(BigDecimal.ZERO);
            BigDecimal balanceAfter = balanceBefore.add(amount);

            // ==========================================
            // 3. 【铁律 2】乐观锁更新钱包余额 (防 Lost Update)
            // 对应SQL: UPDATE t_member_wallet SET cash_balance = cash_balance + #{amount}, version = version + 1
            //         WHERE id = #{id} AND version = #{version}
            // ==========================================
            int updateRows = memberWalletDao.addCashBalanceWithVersion(wallet.getId(), amount, wallet.getVersion());

            if (updateRows == 0) {
                // 如果返回 0，说明在你查出来到更新的这几毫秒内，别的线程改了这个钱包。
                // 抛出异常触发事务回滚，让上层 Engine 捕获或依赖框架的 @Retryable 进行重试！
                log.warn("【动账并发冲突】钱包版本号已变更，触发乐观锁重试. 提案ID: {}", proposal.getId());
                throw new RuntimeException("账户余额变动中，请重试");
            }

            // ==========================================
            // 4. 【铁律 3】写入核心资金流水账单
            // ==========================================
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

            memberAssetTransactionDao.insert(txn);

            log.info(">>>> [动账成功] 提案ID: {}, 充值: {}, 最新余额: {}", proposal.getId(), amount, balanceAfter);
            return ResponseDTO.ok();

        } catch (DuplicateKeyException e) {
            // ==========================================
            // 5. 【铁律 4】终极幂等拦截
            // 依靠 t_member_asset_transaction 的 uk_t_biz_mbr_ast_txn_ref 兜底
            // ==========================================
            log.warn("【动账防重拦截】该提案已存在资金流水，自动视为成功. 提案ID: {}", proposal.getId());
            return ResponseDTO.ok();

        } catch (Exception e) {
            log.error("【账务系统致命异常】执行余额动账失败, 提案ID: {}", proposal.getId(), e);
            throw e; // 抛给 AssetDispatchEngine，让其把提案状态改成 70(彻底失败)
        }
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
