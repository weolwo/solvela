package solvela.member.risk.feature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.api.MemberLoginType;
import solvela.member.risk.MemberRiskProperties;
import solvela.member.risk.RiskContext;
import solvela.member.risk.dao.RiskDeviceDao;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老设备豁免（方案 §4.1）。
 *
 * <h3>钉住的四件事</h3>
 * <ul>
 *   <li>门槛与「成功登录过」两个条件都交给 SQL，这里只钉<b>传下去的参数对不对</b>；</li>
 *   <li>一次尝试只查一次 —— 四条 IP 规则都会问它；</li>
 *   <li>🔴 查不到就<b>不豁免</b>。反过来写的话，数据库一抖动所有人都成了老设备，
 *       而被攻击时数据库恰恰最容易抖；</li>
 *   <li>配成 0 就是关掉。</li>
 * </ul>
 *
 * @Date 2026-10-16
 */
class OldDeviceExemptionTest {

    private RiskDeviceDao dao;

    private MemberRiskProperties properties;

    private OldDeviceExemption exemption;

    @BeforeEach
    void setUp() {
        dao = mock(RiskDeviceDao.class);
        properties = new MemberRiskProperties();
        exemption = new OldDeviceExemption(dao, properties);
    }

    @Test
    @DisplayName("老设备 → 豁免，而且按配置的门槛去问（默认 7 天）")
    void 老设备豁免() {
        when(dao.isOldDevice("dev-1", Duration.ofDays(7).toSeconds(),
                LoginLogResultEnum.LOGIN_SUCCESS.getValue())).thenReturn(true);

        assertTrue(exemption.exempt(ctx("dev-1")));
    }

    @Test
    @DisplayName("🔴 一次尝试只查一次：四条 IP 规则都会问它")
    void 只查一次() {
        RiskContext ctx = ctx("dev-1");
        when(dao.isOldDevice(anyString(), anyLong(), anyInt())).thenReturn(true);

        exemption.exempt(ctx);
        exemption.exempt(ctx);
        exemption.exempt(ctx);

        verify(dao, times(1)).isOldDevice(anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("没带设备令牌 → 不豁免，也不查库")
    void 没有设备号() {
        assertFalse(exemption.exempt(ctx(null)));
        verify(dao, never()).isOldDevice(anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("🔴 查库炸了 → 不豁免。反过来写，数据库一抖动所有人都成了老设备")
    void 查库失败时不豁免() {
        when(dao.isOldDevice(anyString(), anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("连接池满了"));

        assertFalse(exemption.exempt(ctx("dev-1")));
    }

    @Test
    @DisplayName("门槛配成 0 = 关掉这个豁免，连库都不查")
    void 配成零就是关掉() {
        properties.setOldDeviceAge(Duration.ZERO);

        assertFalse(exemption.exempt(ctx("dev-1")));
        verify(dao, never()).isOldDevice(anyString(), anyLong(), anyInt());
    }

    private static RiskContext ctx(String deviceId) {
        return new RiskContext(MemberRiskEventTypeEnum.LOGIN, MemberLoginType.EMAIL_PASSWORD, "a@x.com",
                "ab".repeat(32), null, deviceId, "H5", "1.2.3.4", false);
    }
}
