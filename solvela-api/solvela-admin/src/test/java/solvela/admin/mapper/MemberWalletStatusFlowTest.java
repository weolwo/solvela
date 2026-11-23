package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.PrizeTypeEnum;
import solvela.enums.WalletStatusEnum;
import solvela.ledger.MemberWallet;
import solvela.ledger.wallet.dao.MemberWalletDao;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 钱包加减余额在真库上走一遍，重点是「读完到写之间被冻结」。
 *
 * <p>冻结目前只能直接改库（不动 version），所以以前光靠乐观锁拦不住：读出来是正常、过了
 * {@code checkAvailable}，随后被冻结，扣款照样成功。现在加减余额的 UPDATE 带「钱包正常」，这里用 JDBC
 * 模拟那次人工冻结。数据由用例自己造，结束时回滚；会员号用一个不存在的大数。
 */
@SpringBootTest
@Transactional
class MemberWalletStatusFlowTest {

    private static final long MEMBER_ID = 990000000002L;

    @Autowired
    private MemberWalletDao dao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("🔴 读出来是正常、随后被人工冻结：加、扣都影响 0 行，余额不动")
    void 读后被冻结不能动余额() {
        MemberWallet wallet = new MemberWallet();
        wallet.setMemberId(MEMBER_ID);
        wallet.setAssetType(PrizeTypeEnum.SCORE);
        wallet.setBalance(new BigDecimal("100"));
        wallet.setVersion(0);
        wallet.openNormal();
        dao.insert(wallet);

        MemberWallet read = dao.selectById(wallet.getId());
        assertEquals(WalletStatusEnum.NORMAL, read.getStatus(), "落库后读回的状态不对，或没装配上");
        assertEquals(1, dao.addBalanceWithVersion(read.getId(), new BigDecimal("10"), read.getVersion()));

        MemberWallet beforeFreeze = dao.selectById(wallet.getId());
        // 模拟运营直接改库冻结：只改 status，不动 version
        jdbcTemplate.update("UPDATE t_member_wallet SET status = ? WHERE id = ?",
                WalletStatusEnum.FROZEN.getValue(), wallet.getId());

        assertEquals(0, dao.deductBalanceWithVersion(beforeFreeze.getId(), new BigDecimal("5"), beforeFreeze.getVersion()),
                "冻结的钱包被扣了款");
        assertEquals(0, dao.addBalanceWithVersion(beforeFreeze.getId(), new BigDecimal("5"), beforeFreeze.getVersion()),
                "冻结的钱包被加了款");
        assertEquals(0, new BigDecimal("110").compareTo(dao.selectById(wallet.getId()).getBalance()));
    }
}
