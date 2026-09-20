package solvela.ledger.logistic.service;

import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.ledger.PhysicalDelivery;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履约单上的收件信息是个人信息：<b>加密存储、只有本人看得到、手机号还要脱敏</b>。
 *
 * <h3>为什么要和商城地址簿分开再守一遍</h3>
 * 形状对齐 {@code AddressPrivacyTest}，但守的是<b>另一张表</b>：
 * 地址簿是用户自己维护的通讯录，履约单是<b>单据</b>上的收件快照。
 * 两张表、两套代码路径、两个模块（mall / ledger），
 * 任何一边塌了都不会让另一边的测试变红。
 *
 * <h3>🔴 三条各守一处，任何一条塌了都不会有报错</h3>
 * ① <b>密文落库</b> —— 靠实体上的 {@code PiiTypeHandler}。漏标一个字段，
 *    那一列就以明文入库，而读写完全正常，只有翻库的人才看得见；
 * ② <b>只有本人</b> —— 会员侧的每一个查询都带 memberId；
 * ③ <b>手机号脱敏</b> —— 契约的注释承诺了「138****8000」。
 *    <b>文档承诺了而代码没做</b>是最难发现的一类：读代码的人会相信注释，
 *    然后在别处基于「它已经脱敏了」做决定。
 *
 * @Date 2026-09-18
 */
class DeliveryPrivacyTest {

    /** 库里是密文的三列 */
    private static final List<String> ENCRYPTED =
            List.of("receiverName", "receiverPhone", "receiverAddress");

    private static final Path SERVICE = Path.of("src", "main", "java", "solvela", "ledger",
            "logistic", "service", "MemberDeliveryService.java");

    @Test
    @DisplayName("🔴 三个 PII 字段都挂了 PiiTypeHandler —— 漏一个就是明文入库")
    void 敏感字段都加密() throws NoSuchFieldException {
        for (String name : ENCRYPTED) {
            Field field = PhysicalDelivery.class.getDeclaredField(name);
            TableField anno = field.getAnnotation(TableField.class);
            assertNotNull(anno, name + " 上没有 @TableField");
            assertEquals("PiiTypeHandler", anno.typeHandler().getSimpleName(),
                    () -> name + " 没挂 PiiTypeHandler —— 它会以明文入库，"
                            + "而读写完全正常，只有翻库的人才看得见");
        }
    }

    @Test
    @DisplayName("🔴 奖品名刻意【不】加密：C 端要显示，后台统计也要用")
    void 奖品名保持明文() throws NoSuchFieldException {
        TableField anno = PhysicalDelivery.class.getDeclaredField("prizeName")
                .getAnnotation(TableField.class);
        boolean encrypted = anno != null && "PiiTypeHandler".equals(anno.typeHandler().getSimpleName());
        assertFalse(encrypted, "prizeName 不该加密 —— 它识别不到具体自然人，"
                + "而加了之后后台按奖品维度的发货统计会全废");
    }

    /* ---------------- 脱敏 ---------------- */

    @Test
    @DisplayName("🔴 大陆号码留头 3 尾 4")
    void 手机号脱敏() {
        assertEquals("138****8000", MemberDeliveryService.maskPhone("13800008000"));
    }

    @Test
    @DisplayName("非大陆号码也不能露出全貌 —— 库里已经有菲律宾的地址")
    void 非大陆号码同样脱敏() {
        String masked = MemberDeliveryService.maskPhone("+639171234567");
        assertAll(
                () -> assertTrue(masked.contains("****"), masked),
                () -> assertFalse(masked.contains("9171234"), "中间段泄漏了: " + masked));
    }

    @Test
    @DisplayName("🔴 任何输入下，露出的字符都不会比原号码多")
    void 短号不会反而暴露更多() {
        for (String phone : List.of("1", "12", "123", "1234567", "12345678", "13800008000")) {
            String masked = MemberDeliveryService.maskPhone(phone);
            long visible = masked.chars().filter(ch -> ch != '*').count();
            assertTrue(visible < phone.length() || phone.length() <= 2,
                    () -> "「" + phone + "」脱敏成「" + masked + "」，没遮住多少");
        }
    }

    @Test
    @DisplayName("空值原样返回，不要拼出一串星号让前端以为有号码")
    void 空值不编造() {
        assertEquals(null, MemberDeliveryService.maskPhone(null));
        assertEquals("", MemberDeliveryService.maskPhone(""));
    }

    /* ---------------- 暴露面 ---------------- */

    @Test
    @DisplayName("🔴 下发给 C 端的履约单一律走 toView —— 它是唯一的脱敏关口")
    void 没有绕过脱敏的出口() throws IOException {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8);

        /*
         * new MemberDeliveryView(...) 只该出现一次 —— 就在 toView 里。
         * 多出一处就意味着有人绕过了脱敏，而那条路径不会有任何报错。
         */
        int constructions = source.split("new MemberDeliveryView\\(", -1).length - 1;
        assertEquals(1, constructions,
                "MemberDeliveryView 被构造了 " + constructions + " 次；"
                        + "只允许 toView 一处，否则脱敏会被绕过");
        assertTrue(source.contains("maskPhone(entity.getReceiverPhone())"),
                "toView 没有脱敏手机号 —— 而契约的注释承诺了它是脱敏值");
    }

    @Test
    @DisplayName("🔴 会员侧的每一次查询都带 memberId —— 少了它就是「能查到别人的收货地址」")
    void 查询都限定本人() throws IOException {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8);

        /*
         * 判据很粗但有效：本类里出现几次 LambdaQueryWrapper，就该出现几次
         * getMemberId 的等值条件。
         *
         * 它拦不住所有写法（有人硬要拼 last("...") 就绕过去了），
         * 但拦得住最可能发生的那一种：复制一段查询、改了字段、忘了带 memberId。
         * 那种改动看起来完全正常，跑起来也完全正常 —— 只是返回了别人的数据。
         */
        int wrappers = source.split("new LambdaQueryWrapper<PhysicalDelivery>\\(\\)", -1).length - 1;
        int memberScoped = source.split("PhysicalDelivery::getMemberId", -1).length - 1;
        assertTrue(wrappers > 0, "一个查询都没扫到，这条测试的判据失效了");
        assertEquals(wrappers, memberScoped,
                "有 " + wrappers + " 处查询，但只有 " + memberScoped + " 处限定了 memberId。"
                        + "会员侧的每一次读写都必须把 memberId 放进 WHERE —— "
                        + "少了它就是「能查到、能改别人的收货地址」，而且不报错");
    }
}
