package solvela.member.risk.feature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.json.JsonUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 的网段与机房判定。
 *
 * <h3>这里钉的是「同一个地址的多种写法要落到同一个网段」</h3>
 * 按字符串截前几段对 IPv4 看着能用，对 IPv6 的各种缩写完全不成立。
 * 而那种 bug 的表现是「常用网段永远对不上」，于是熟悉网络免滑块这件事静默失效 ——
 * 用户只会觉得「每次登录都要拖图」，没人会报障说「我的网段判错了」。
 *
 * <p>归属地那部分不在这里测：它依赖 ip2region 的数据文件，而<b>这个测试不该因为换了一份数据就红</b>。
 * 位置解析本身由 {@code SolvelaIpUtil.getRegionFields} 负责。
 *
 * @Date 2026-10-15
 */
@DisplayName("IP 情报")
class IpIntelTest {

    private final IpIntel ipIntel = new IpIntel();

    @Test
    @DisplayName("IPv4 按 /24 归并：同一网段的不同主机落到同一个键")
    void v4Prefix() {
        assertEquals(ipIntel.prefix("1.2.3.4", 24, 64), ipIntel.prefix("1.2.3.200", 24, 64));
        assertNotEquals(ipIntel.prefix("1.2.3.4", 24, 64), ipIntel.prefix("1.2.4.4", 24, 64));
    }

    @Test
    @DisplayName("🔴 IPv6 的缩写与展开写法必须落到同一个网段")
    void v6Canonical() {
        String compact = ipIntel.prefix("2001:db8::1", 24, 64);
        String expanded = ipIntel.prefix("2001:0db8:0000:0000:0000:0000:0000:0001", 24, 64);

        assertEquals(compact, expanded, "按字符串截断的话这两个会被判成不同网段");
    }

    @Test
    @DisplayName("🔴 IPv6 只比前 64 位：手机的隐私地址每天换后 64 位，否则同一个人每次都是新网段")
    void v6IgnoresHostPart() {
        assertEquals(ipIntel.prefix("2001:db8:1:2::abcd", 24, 64),
                ipIntel.prefix("2001:db8:1:2::ffff", 24, 64));
        assertNotEquals(ipIntel.prefix("2001:db8:1:2::1", 24, 64),
                ipIntel.prefix("2001:db8:1:3::1", 24, 64));
    }

    @Test
    @DisplayName("IPv4 与 IPv6 的网段键不会撞在一起")
    void versionsDoNotCollide() {
        assertNotEquals(ipIntel.prefix("1.2.3.4", 24, 64), ipIntel.prefix("::1.2.3.4", 24, 64));
    }

    @Test
    @DisplayName("不是地址字面量时返回 null，而且🔴不去做 DNS 解析（那是一次网络往返，而这在登录路径上）")
    void garbageReturnsNull() {
        assertNull(ipIntel.prefix("not-an-ip", 24, 64));
        assertNull(ipIntel.prefix("", 24, 64));
        assertNull(ipIntel.prefix(null, 24, 64));
        assertNull(ipIntel.prefix("localhost", 24, 64), "有主机名的话绝不能去查 DNS");
        assertNull(ipIntel.prefix("example.com", 24, 64));
    }

    @Test
    @DisplayName("前缀位数越界时不抛，按边界处理")
    void prefixBoundsClamped() {
        assertEquals("4:/0", ipIntel.prefix("1.2.3.4", 0, 64));
        assertEquals(ipIntel.prefix("1.2.3.4", 32, 64), ipIntel.prefix("1.2.3.4", 999, 64));
    }

    @Test
    @DisplayName("机房判定按关键字、大小写不敏感")
    void datacenterKeywords() {
        var params = JsonUtils.getMapper().readTree("{\"keywords\":[\"阿里云\",\"Amazon\"]}");

        assertTrue(ipIntel.datacenter(location("阿里云"), params));
        assertTrue(ipIntel.datacenter(location("AMAZON Data Services"), params), "大小写不该影响判定");
        assertFalse(ipIntel.datacenter(location("电信"), params));
        assertFalse(ipIntel.datacenter(location(null), params), "运营商未知时不判，而不是当成机房");
    }

    @Test
    @DisplayName("关键字为空时永远不命中 —— 所以规则的 validate 会拒绝保存空列表")
    void emptyKeywordsNeverHit() {
        assertFalse(ipIntel.datacenter(location("阿里云"),
                JsonUtils.getMapper().readTree("{\"keywords\":[]}")));
        assertFalse(ipIntel.datacenter(location("阿里云"), JsonUtils.getMapper().readTree("{}")));
    }

    @Test
    @DisplayName("城市键三段齐了才给，缺一段就返回 null —— 宁可不判，也不要拿半个地名去比")
    void cityKeyNeedsAllThree() {
        assertEquals("中国|广东省|深圳市", new IpIntel.Location("中国", "广东省", "深圳市", "电信", null).cityKey());
        assertNull(new IpIntel.Location("中国", null, "深圳市", "电信", null).cityKey());
        assertNull(new IpIntel.Location("中国", "广东省", null, "电信", null).cityKey());
        assertNull(new IpIntel.Location("中国", "广东省", "深圳市", null, null).cityIspKey(),
                "宽松口径要运营商，缺了就不给");
    }

    @Test
    @DisplayName("连国家都解析不出来 → unknown，地理类规则一律不判")
    void unknownLocation() {
        assertTrue(new IpIntel.Location(null, null, null, null, null).unknown());
        assertTrue(new IpIntel.Location("内网IP", null, null, null, null).intranet());
    }

    private static IpIntel.Location location(String isp) {
        return new IpIntel.Location("中国", "广东省", "深圳市", isp, "4:1.2.3/24");
    }
}
