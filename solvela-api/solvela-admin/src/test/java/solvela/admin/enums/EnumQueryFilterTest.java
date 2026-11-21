package solvela.admin.enums;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.DeviceTypeEnum;
import solvela.enums.MallOrderSourceEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.enums.RegisterSourceEnum;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.mall.commodity.domain.query.MallCommodityQuery;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.domain.query.MallOrderQuery;
import solvela.member.dao.MemberDao;
import solvela.member.device.dao.DeviceDao;
import solvela.member.device.domain.query.DeviceQuery;
import solvela.member.domain.query.MemberQuery;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.loginlog.domain.query.MemberLoginLogQuery;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 把类型筛选条件设上、真跑一遍查询。
 *
 * <p>🔴 列改成枚举之后，mapper 里 {@code <if test="x != null and x != ''">} 这种写法会在 MyBatis 的 OGNL 里抛
 * 「invalid comparison: XxxEnum and java.lang.String」—— 编译、单测都发现不了，只在有人真去按类型筛选时才炸。
 * 2026-11-21 第二批枚举化时，商城商品 / 订单、设备、登录日志、会员五个 mapper 都踩过（会员那条由 CanaryAccountTest 撞出来）。
 * 以前没有任何用例会带着类型筛选去查，所以它们一直绿着。
 *
 * <p>不关心查出几行，只关心带着枚举筛选能不能查。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class EnumQueryFilterTest {

    @Autowired
    private MallCommodityDao mallCommodityDao;

    @Autowired
    private MallOrderDao mallOrderDao;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private MemberLoginLogDao memberLoginLogDao;

    @Autowired
    private MemberDao memberDao;

    @Test
    @DisplayName("🔴 带着枚举类型的筛选条件，五个列表查询都能跑")
    void 枚举筛选能查() {
        MallCommodityQuery commodity = new MallCommodityQuery();
        commodity.setCommodityType(PrizeTypeEnum.PHYSICAL);

        MallOrderQuery order = new MallOrderQuery();
        order.setCommodityType(PrizeTypeEnum.COUPON);
        order.setSourceType(MallOrderSourceEnum.NORMAL);

        DeviceQuery device = new DeviceQuery();
        device.setDeviceType(DeviceTypeEnum.APP);

        MemberLoginLogQuery loginLog = new MemberLoginLogQuery();
        loginLog.setDeviceType(DeviceTypeEnum.H5);

        MemberQuery member = new MemberQuery();
        member.setRegisterSource(RegisterSourceEnum.H5);

        assertAll(
                () -> assertDoesNotThrow(() -> mallCommodityDao.queryPage(new Page<>(1, 1), commodity), "商城商品"),
                () -> assertDoesNotThrow(() -> mallOrderDao.queryPage(new Page<>(1, 1), order), "商城订单"),
                () -> assertDoesNotThrow(() -> deviceDao.queryPage(new Page<>(1, 1), device), "设备"),
                () -> assertDoesNotThrow(() -> memberLoginLogDao.queryPage(new Page<>(1, 1), loginLog), "登录日志"),
                () -> assertDoesNotThrow(() -> memberDao.queryPage(new Page<>(1, 1), member), "会员"));
    }
}
