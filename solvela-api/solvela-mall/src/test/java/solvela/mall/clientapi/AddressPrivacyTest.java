package solvela.mall.clientapi;

import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.mall.MallAddress;

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
 * 地址簿是个人信息：<b>加密存储，只有本人看得到，手机号还要脱敏</b>。
 *
 * <h3>三条各守一处，任何一条塌了都不会有报错</h3>
 * ① <b>密文落库</b> —— 靠实体上的 {@code PiiTypeHandler}。漏标一个字段，
 *    那一列就以明文入库，而读写完全正常，只有翻库的人才看得见；
 * ② <b>只有本人</b> —— 所有查询都带 memberId，后台没有地址簿入口；
 * ③ <b>手机号脱敏</b> —— 契约的注释早就承诺了「138****8000」，
 *    但代码一直传的是解密后的明文。<b>文档承诺了而代码没做</b>是最难发现的一类：
 *    读代码的人会相信注释，然后在别处基于「它已经脱敏了」做决定。
 *
 * @Date 2026-09-05
 */
class AddressPrivacyTest {

    /** 库里是密文的三列。省市区刻意<b>不</b>加密：后台的物流成本统计要用 */
    private static final List<String> ENCRYPTED = List.of(
            "receiverName", "receiverPhone", "detailAddress");

    private static final List<String> PLAINTEXT = List.of("province", "city", "district");

    @Test
    @DisplayName("🔴 三个 PII 字段都挂了 PiiTypeHandler —— 漏一个就是明文入库")
    void 敏感字段都加密() throws NoSuchFieldException {
        for (String name : ENCRYPTED) {
            Field field = MallAddress.class.getDeclaredField(name);
            TableField anno = field.getAnnotation(TableField.class);
            assertNotNull(anno, name + " 上没有 @TableField");
            assertEquals("PiiTypeHandler", anno.typeHandler().getSimpleName(),
                    () -> name + " 没挂 PiiTypeHandler —— 它会以明文入库，"
                            + "而读写完全正常，只有翻库的人才看得见");
        }
    }

    @Test
    @DisplayName("省市区刻意不加密：加了的话后台的发货分布与物流成本统计全废")
    void 省市区保持明文() throws NoSuchFieldException {
        for (String name : PLAINTEXT) {
            TableField anno = MallAddress.class.getDeclaredField(name).getAnnotation(TableField.class);
            boolean encrypted = anno != null && "PiiTypeHandler".equals(anno.typeHandler().getSimpleName());
            assertFalse(encrypted, name + " 不该加密 —— 它识别不到具体自然人，但统计要用");
        }
    }

    /* ---------------- 脱敏 ---------------- */

    @Test
    @DisplayName("🔴 大陆号码留头 3 尾 4")
    void 手机号脱敏() {
        assertEquals("138****8000", MallClientFacade.maskPhone("13800008000"));
    }

    @Test
    @DisplayName("非大陆号码也不能露出全貌 —— 库里已经有菲律宾的地址")
    void 非大陆号码同样脱敏() {
        String masked = MallClientFacade.maskPhone("+639171234567");
        assertAll(
                () -> assertTrue(masked.contains("****"), masked),
                () -> assertFalse(masked.contains("9171234"), "中间段泄漏了: " + masked));
    }

    @Test
    @DisplayName("🔴 任何输入下，露出的字符都不会比原号码多")
    void 短号不会反而暴露更多() {
        for (String phone : List.of("1", "12", "123", "1234567", "12345678", "13800008000")) {
            String masked = MallClientFacade.maskPhone(phone);
            long visible = masked.chars().filter(ch -> ch != '*').count();
            assertTrue(visible < phone.length() || phone.length() <= 2,
                    () -> "「" + phone + "」脱敏成「" + masked + "」，没遮住多少");
        }
    }

    @Test
    @DisplayName("空值原样返回，不要拼出一串星号让前端以为有号码")
    void 空值不编造() {
        assertEquals(null, MallClientFacade.maskPhone(null));
        assertEquals("", MallClientFacade.maskPhone(""));
    }

    /* ---------------- 暴露面 ---------------- */

    @Test
    @DisplayName("🔴 下发给 C 端的地址一律走 toAddressView —— 它是唯一的脱敏关口")
    void 没有绕过脱敏的出口() throws IOException {
        Path facade = Path.of("src", "main", "java", "solvela", "mall",
                "clientapi", "MallClientFacade.java");
        String source = Files.readString(facade, StandardCharsets.UTF_8);

        /*
         * new MallAddressView(...) 只该出现一次 —— 就在 toAddressView 里。
         * 多出一处就意味着有人绕过了脱敏，而那条路径不会有任何报错。
         */
        int constructions = source.split("new MallAddressView\\(", -1).length - 1;
        assertEquals(1, constructions,
                "MallAddressView 被构造了 " + constructions + " 次；"
                        + "只允许 toAddressView 一处，否则脱敏会被绕过");
        assertTrue(source.contains("maskPhone(a.getReceiverPhone())"),
                "toAddressView 没有脱敏手机号 —— 而契约的注释承诺了它是脱敏值");
    }
}
