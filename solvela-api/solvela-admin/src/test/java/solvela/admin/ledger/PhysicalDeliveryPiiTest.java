package solvela.admin.ledger;

import solvela.enums.DeliveryStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.crypto.PiiCipher;
import solvela.base.domain.PageResult;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.domain.dto.PhysicalDeliveryDTO;
import solvela.ledger.logistic.domain.query.PhysicalDeliveryQuery;
import solvela.ledger.logistic.service.PhysicalDeliveryService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收件信息落库加密的<b>端到端</b>验证（真连库）。
 *
 * <p>🔴 为什么必须有这个测试：{@code PiiCipherTest} 只证明了「算法本身能加能解」，
 * 而这次改造真正会出错的地方是<b>接线</b>——
 * <ul>
 *   <li>实体上漏了 {@code autoResultMap = true}：写进去是密文，查出来还是密文；</li>
 *   <li>VO 的 resultMap 上漏了 {@code typeHandler}：列表页一片 {@code P1:...}；</li>
 *   <li>某条写入路径没走实体（比如手写 XML 的 INSERT）：那一行明文进库。</li>
 * </ul>
 * 这三种<b>都不会抛异常</b>，编译和单测也全绿。只有真写一行、再回头看库里到底存的是什么，
 * 才能证明它生效了。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class PhysicalDeliveryPiiTest {

    @Autowired
    private PhysicalDeliveryDao physicalDeliveryDao;
    @Autowired
    private PhysicalDeliveryService physicalDeliveryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String NAME = "张三丰";
    private static final String PHONE = "13800138000";
    private static final String ADDRESS = "浙江省杭州市西湖区文三路 100 号 xx 大厦 A 座 1801 室";

    /** 造数用的会员号，不落 t_member —— 本测试验的是加解密接线，不 join 会员表 */
    private static final long MEMBER_ID = 1000000001L;

    @Test
    @DisplayName("🔴 收件三项：库里存的是密文，读出来是明文（列表页也一样）")
    void receiverFieldsAreEncryptedAtRestAndDecryptedOnRead() {
        String sourceBizId = "PII_TEST_" + System.nanoTime();
        PhysicalDelivery entity = new PhysicalDelivery();
        entity.setMemberId(MEMBER_ID);
        entity.setMemberName("pii_test");
        entity.setSourceBizId(sourceBizId);
        entity.setSourceType("TEST");
        entity.setReceiverName(NAME);
        entity.setReceiverPhone(PHONE);
        entity.setReceiverAddress(ADDRESS);
        entity.setStatus(DeliveryStatusEnum.PENDING);

        physicalDeliveryDao.insert(entity);
        Long id = entity.getId();
        assertNotNull(id);

        try {
            // ---------- ① 库里：必须是密文 ----------
            // 走 jdbcTemplate 而不是 DAO：DAO 会自动解密，用它取值等于什么都没验
            Map<String, Object> raw = jdbcTemplate.queryForMap(
                    "SELECT receiver_name, receiver_phone, receiver_address"
                            + " FROM t_physical_delivery WHERE id = ?", id);
            for (Object col : raw.values()) {
                assertTrue(String.valueOf(col).startsWith(PiiCipher.PREFIX),
                        "库里存的不是密文，加密没生效：" + col);
            }
            String rawJoined = String.join("|", raw.values().stream().map(String::valueOf).toList());
            assertFalse(rawJoined.contains(NAME), "库里能直接搜到明文姓名");
            assertFalse(rawJoined.contains(PHONE), "库里能直接搜到明文电话");
            assertFalse(rawJoined.contains("文三路"), "库里能直接搜到明文地址");

            // ---------- ② 实体读：必须是明文（漏了 autoResultMap 会在这里挂） ----------
            PhysicalDelivery loaded = physicalDeliveryDao.selectById(id);
            assertEquals(NAME, loaded.getReceiverName());
            assertEquals(PHONE, loaded.getReceiverPhone());
            assertEquals(ADDRESS, loaded.getReceiverAddress());

            // ---------- ③ 列表 VO：必须是明文（漏了 resultMap 的 typeHandler 会在这里挂） ----------
            PhysicalDeliveryQuery queryForm = new PhysicalDeliveryQuery();
            queryForm.setSourceBizId(sourceBizId);
            queryForm.setPageNum(1L);
            queryForm.setPageSize(10L);
            PageResult<PhysicalDeliveryDTO> page = physicalDeliveryService.queryPage(queryForm);
            assertEquals(1, page.list().size(), "按来源单号没查到刚插的那条");
            PhysicalDeliveryDTO vo = page.list().get(0);
            assertEquals(NAME, vo.getReceiverName());
            assertEquals(PHONE, vo.getReceiverPhone());
            assertEquals(ADDRESS, vo.getReceiverAddress());
            assertEquals(sourceBizId, vo.getSourceBizId());

            // ---------- ④ 同一明文两次写入，密文必须不同 ----------
            // 相同就是退化成 ECB：不用解密也能按密文分组数出「哪些人住同一个地址」
            PhysicalDelivery second = new PhysicalDelivery();
            second.setMemberId(MEMBER_ID);
            second.setSourceBizId(sourceBizId + "_2");
            second.setSourceType("TEST");
            second.setReceiverAddress(ADDRESS);
            second.setStatus(DeliveryStatusEnum.PENDING);
            physicalDeliveryDao.insert(second);
            try {
                String rawA = jdbcTemplate.queryForObject(
                        "SELECT receiver_address FROM t_physical_delivery WHERE id = ?", String.class, id);
                String rawB = jdbcTemplate.queryForObject(
                        "SELECT receiver_address FROM t_physical_delivery WHERE id = ?",
                        String.class, second.getId());
                assertFalse(rawA.equals(rawB), "同一地址两次加密得到相同密文 —— 退化成 ECB 了");
            } finally {
                physicalDeliveryDao.deleteById(second.getId());
            }
        } finally {
            physicalDeliveryDao.deleteById(id);
        }
    }

    @Test
    @DisplayName("🔴 收件信息为空时不加密：否则「待补地址」的统计会静默归零")
    void blankReceiverStaysBlank() {
        // selectStatusStat 用 `receiver_address IS NULL OR receiver_address = ''`
        // 数「收件信息没补全、想发也发不了」的单子。把空值也加密成一段密文，
        // 那个数字会永远是 0，而运营正是靠它去催用户的。
        String sourceBizId = "PII_BLANK_" + System.nanoTime();
        PhysicalDelivery entity = new PhysicalDelivery();
        entity.setMemberId(MEMBER_ID);
        entity.setSourceBizId(sourceBizId);
        entity.setSourceType("TEST");
        entity.setReceiverName("");
        entity.setStatus(DeliveryStatusEnum.PENDING);
        physicalDeliveryDao.insert(entity);

        try {
            String rawName = jdbcTemplate.queryForObject(
                    "SELECT receiver_name FROM t_physical_delivery WHERE id = ?", String.class, entity.getId());
            assertEquals("", rawName, "空串被加密了 —— 「待补地址」的统计会就此归零");

            String rawAddress = jdbcTemplate.queryForObject(
                    "SELECT receiver_address FROM t_physical_delivery WHERE id = ?", String.class, entity.getId());
            assertNull(rawAddress, "null 被写成了别的东西");

            Long pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_physical_delivery WHERE id = ?"
                            + " AND (receiver_address IS NULL OR receiver_address = '')",
                    Long.class, entity.getId());
            assertEquals(1L, pending, "这一行应当被「收件信息没补全」的条件命中");
        } finally {
            physicalDeliveryDao.deleteById(entity.getId());
        }
    }

    @Test
    @DisplayName("唯一键已换成 (source_biz_id, source_type)：商城订单号也能落履约单")
    void sourceBizIdIsTheKeyNotProposalId() {
        // 换键之前这一列是 proposal_id bigint NOT NULL，商城订单没有提案 ID，
        // 那条路径下这张表根本插不进去 —— 实物商品也就落不了发货单
        String orderNo = "MALL" + System.nanoTime();
        PhysicalDelivery entity = new PhysicalDelivery();
        entity.setMemberId(MEMBER_ID);
        entity.setSourceBizId(orderNo);
        entity.setSourceType("MALL");
        entity.setStatus(DeliveryStatusEnum.PENDING);
        physicalDeliveryDao.insert(entity);

        try {
            List<PhysicalDelivery> list = physicalDeliveryDao.selectList(
                    new LambdaQueryWrapper<PhysicalDelivery>()
                            .eq(PhysicalDelivery::getSourceBizId, orderNo));
            assertEquals(1, list.size());
            assertEquals("MALL", list.get(0).getSourceType());
        } finally {
            physicalDeliveryDao.deleteById(entity.getId());
        }
    }
}
