package solvela.admin.enums;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.PrizeTypeEnum;
import solvela.enums.WalletStatusEnum;
import solvela.ledger.MemberWallet;
import solvela.ledger.wallet.dao.MemberWalletDao;
import solvela.ledger.wallet.domain.dto.MemberWalletDTO;
import solvela.ledger.wallet.domain.dto.MemberWalletStatDTO;
import solvela.ledger.wallet.domain.query.MemberWalletQuery;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * prize_type / asset_type 改成 PrizeTypeEnum 之后，三条读写路径在真库上走一遍（以 t_member_wallet 为代表）：
 * <ul>
 *   <li>BaseMapper 的插入与按 id 查；</li>
 *   <li>手写 XML 把枚举当查询参数（{@code #{assetType}}）、结果走 resultMap；</li>
 *   <li>GROUP BY 出来的 Map 行经 {@code StatRow.enumValue} 转回枚举。</li>
 * </ul>
 * 库里存的必须仍是枚举名 —— 这一点直接用 SQL 读原值断言，不经过任何映射。
 *
 * <p>数据自己造、自己删，会员 id 取一个不会撞上真实数据的值。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class PrizeTypeColumnRoundTripTest {

    private static final long MEMBER_ID = 990_000_000_123L;

    @Autowired
    private MemberWalletDao memberWalletDao;

    @Autowired
    private MemberWalletService memberWalletService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_member_wallet WHERE member_id = ?", MEMBER_ID);
    }

    @Test
    @DisplayName("🔴 枚举落库仍是枚举名，三条读路径都能读回枚举")
    void 钱包资产类型往返() {
        MemberWallet wallet = new MemberWallet();
        wallet.setMemberId(MEMBER_ID);
        wallet.setAssetType(PrizeTypeEnum.BALANCE);
        wallet.setBalance(new BigDecimal("12.3400"));
        wallet.setStatus(WalletStatusEnum.NORMAL);
        wallet.setVersion(0);
        memberWalletDao.insert(wallet);

        String stored = jdbcTemplate.queryForObject(
                "SELECT asset_type FROM t_member_wallet WHERE id = ?", String.class, wallet.getId());
        MemberWallet byId = memberWalletDao.selectById(wallet.getId());
        MemberWallet byXml = memberWalletDao.getByMemberIdAndAssetType(MEMBER_ID, PrizeTypeEnum.BALANCE);

        MemberWalletQuery query = new MemberWalletQuery();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setMemberId(MEMBER_ID);
        query.setAssetType(PrizeTypeEnum.BALANCE);
        List<MemberWalletDTO> page = memberWalletService.queryPage(query).list();

        MemberWalletStatDTO stat = memberWalletService.stat(new LedgerStatQuery());

        assertAll(
                () -> assertEquals("BALANCE", stored, "库里存的不是枚举名，库与接口的形状就变了"),
                () -> assertEquals(PrizeTypeEnum.BALANCE, byId.getAssetType()),
                () -> assertNotNull(byXml, "XML 里 #{assetType} 绑定枚举后查不到行"),
                () -> assertEquals(PrizeTypeEnum.BALANCE, byXml.getAssetType()),
                () -> assertEquals(1, page.size(), "按枚举过滤的分页查询没查到刚插入的那一行"),
                () -> assertEquals(PrizeTypeEnum.BALANCE, page.getFirst().getAssetType()),
                () -> assertTrue(stat.getAssetList().stream()
                                .anyMatch(a -> a.getAssetType() == PrizeTypeEnum.BALANCE),
                        "GROUP BY 出来的类型没转回枚举"));
    }
}
