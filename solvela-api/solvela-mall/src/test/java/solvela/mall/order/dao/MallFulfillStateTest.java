package solvela.mall.order.dao;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.MallOrderStatusEnum;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履约那三条 SQL 里的状态数字，必须和 {@link MallOrderStatusEnum} 对得上。
 *
 * <h3>为什么需要这个测试</h3>
 * 状态迁移写在 SQL 的 WHERE 里（这是对的：条件 UPDATE 才能在并发下当闸门用），
 * 代价是<b>那几个数字绕过了编译器</b>。改枚举取值时它们不会报错，
 * 只会变成一条永远匹配不到行的 UPDATE —— 表现是「兑换成功了，但东西永远发不出去」，
 * 而且没有任何异常。
 *
 * <p>枚举自己的注释里写着「取值是跳跃的，别写 {@code status < 30} 这种范围判断」，
 * 正是因为这些值会被这样硬编码进 SQL。这个测试是那句话的执行者。
 *
 * @Date 2026-09-05
 */
class MallFulfillStateTest {

    /** {@code SET status = 20} 里那个数 */
    private static final Pattern SET_STATUS = Pattern.compile("SET\\s+status\\s*=\\s*(\\d+)");

    /** {@code AND status = 10} 里那个数 —— 这是闸门的前置状态 */
    private static final Pattern WHERE_STATUS = Pattern.compile("AND\\s+status\\s*=\\s*(\\d+)");

    @Test
    @DisplayName("🔴 待履约 → 履约中：闸门的两端都不能写错")
    void 抢闸门的状态对得上枚举() {
        String sql = sqlOf("markFulfilling");
        assertAll("markFulfilling", sql,
                MallOrderStatusEnum.PENDING, MallOrderStatusEnum.FULFILLING);
    }

    @Test
    @DisplayName("履约中 → 已完成")
    void 完成的状态对得上枚举() {
        String sql = sqlOf("markFinished");
        assertAll("markFinished", sql,
                MallOrderStatusEnum.FULFILLING, MallOrderStatusEnum.FINISHED);
    }

    @Test
    @DisplayName("履约中 → 履约失败")
    void 失败的状态对得上枚举() {
        String sql = sqlOf("markFailed");
        assertAll("markFailed", sql,
                MallOrderStatusEnum.FULFILLING, MallOrderStatusEnum.FAILED);
    }

    @Test
    @DisplayName("🔴 三条 SQL 都不许自己写 update_time —— 那一列归数据库时钟管")
    void 不许手填更新时间() {
        for (String method : new String[]{"markFulfilling", "markFinished", "markFailed"}) {
            String sql = sqlOf(method).toLowerCase();
            /*
             * 铁律 9：create_time / update_time 只认数据库时钟。
             * t_mall_order.update_time 是 ON UPDATE CURRENT_TIMESTAMP，自己会填。
             * 手填的害处不是多写一列，是应用服务器和数据库的时钟一旦有偏差，
             * 按时间排的运营列表就会乱序，而没人会想到去查时钟。
             */
            assertTrue(!sql.contains("update_time"),
                    method + " 的 SQL 里出现了 update_time，那一列该由数据库自己填");
        }
    }

    /* ---------------- 工具 ---------------- */

    private static void assertAll(String method, String sql,
                                  MallOrderStatusEnum from, MallOrderStatusEnum to) {
        assertEquals(from.getValue(), extract(WHERE_STATUS, sql, method, "WHERE"),
                () -> method + " 的前置状态该是 " + from + "(" + from.getValue() + ")，SQL: " + sql);
        assertEquals(to.getValue(), extract(SET_STATUS, sql, method, "SET"),
                () -> method + " 的目标状态该是 " + to + "(" + to.getValue() + ")，SQL: " + sql);
    }

    private static Integer extract(Pattern pattern, String sql, String method, String clause) {
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(),
                () -> method + " 的 SQL 里找不到 " + clause + " 上的 status 条件。"
                        + "改写这条 SQL 时请连同本测试一起改 —— 别把它删掉了事，"
                        + "那几个数字没有编译器管。SQL: " + sql);
        return Integer.valueOf(matcher.group(1));
    }

    private static String sqlOf(String methodName) {
        for (Method method : MallOrderDao.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Update update = method.getAnnotation(Update.class);
                assertTrue(update != null, methodName + " 上没有 @Update 注解了");
                return String.join(" ", update.value());
            }
        }
        throw new AssertionError("MallOrderDao 上找不到方法 " + methodName
                + " —— 履约状态机改过了，本测试要跟着改");
    }
}
