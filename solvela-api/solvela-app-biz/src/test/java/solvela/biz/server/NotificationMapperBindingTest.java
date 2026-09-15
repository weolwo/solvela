package solvela.biz.server;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import solvela.notification.dao.AnnouncementAckDao;
import solvela.notification.dao.AnnouncementDao;
import solvela.notification.dao.MemberAnnouncementCursorDao;
import solvela.notification.dao.MemberNotificationDao;
import solvela.notification.dao.MemberNotificationPreferenceDao;
import solvela.notification.dao.NotificationTemplateDao;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.template.dao.CouponTemplateDao;
import solvela.ledger.coupon.template.dao.CouponWriteOffDao;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知域每个 Dao 方法都必须有对应的 MyBatis 语句。
 *
 * <h3>🔴 这条测试对应一个真实事故：整个 mapper 文件漏掉了，一路没人发现</h3>
 * 2026-09-15：{@code MemberNotificationPreferenceMapper.xml} 压根没被创建出来
 * （创建它的那条命令超时转了后台）。而它一路过了三道关：
 *
 * <ul>
 *   <li><b>编译期</b> —— mapper XML 不参与编译，Dao 接口本身语法没问题；</li>
 *   <li><b>单元测试</b> —— {@code NotificationPreferenceServiceTest} 把 Dao mock 掉了，
 *       正好绕开了它；</li>
 *   <li><b>全量 970 个测试</b> —— 没有一条真的调到 {@code upsert}。</li>
 * </ul>
 *
 * 一直到起了服务、真调 {@code /internal/notification/preference} 才炸出
 * {@code Invalid bound statement (not found)}。
 *
 * <h3>为什么 MyBatis 自己不在启动时报</h3>
 * 它是<b>懒解析</b>的：Dao 接口的代理方法要到<b>第一次被调用</b>时才去 Configuration
 * 里找语句。所以「少一个 XML」「namespace 写错一个字」「方法名改了 XML 没跟着改」
 * 这三类问题都只在运行期、且只在那条路径被走到时才暴露。
 *
 * <p>本测试把每个方法名拿去 {@code Configuration.hasStatement} 点一次名 ——
 * 不执行 SQL，只问「这条语句存不存在」。
 *
 * <h3>⚠️ 它覆盖不到什么</h3>
 * 只查绑定，<b>不查 SQL 写得对不对</b>：列名拼错、条件写反，这里一样过。
 * 那些要靠 {@code NotificationLiveTest} 那种真库用例。
 *
 * <h3>⚠️ 本测试已经不只管通知了</h3>
 * 2026-09-15 把券模块的两个 Dao 也纳了进来 —— {@code CouponTemplate} 同样是
 * 复合主键（{@code coupon_code + version}），踩的是同一个坑。
 * 类名保持不变是为了不打断 git 历史，但它实际上是「本仓 mapper 绑定守卫」。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
class NotificationMapperBindingTest {

    /** MyBatis-Plus 基类自带的方法不需要 XML，跳过 —— 只查我们自己声明的 */
    private static final List<Class<?>> DAOS = List.of(
            NotificationTemplateDao.class,
            MemberNotificationDao.class,
            MemberNotificationPreferenceDao.class,
            AnnouncementDao.class,
            MemberAnnouncementCursorDao.class,
            AnnouncementAckDao.class,
            // 2026-09-15 券模块跟着纳进来。CouponTemplate 也是复合主键
            //（coupon_code + version），和通知模板同一个坑
            CouponTemplateDao.class,
            CouponWriteOffDao.class,
            // 2026-09-15 阶段 3：三阶段核销的 SQL 全是手写的条件更新，
            // 而那三条 WHERE 就是并发闸本身 —— 绑不上的话核销整条路都是死的
            MemberCouponDao.class);

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("🔴 通知域每个自声明的 Dao 方法都能找到对应语句")
    void 每个Dao方法都有绑定() {
        var configuration = sqlSessionFactory.getConfiguration();
        List<String> missing = new ArrayList<>();

        for (Class<?> dao : DAOS) {
            // getDeclaredMethods 只拿本接口声明的 —— BaseMapper 继承来的那些
            // 由 MyBatis-Plus 注入，不需要 XML
            for (Method method : dao.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isDefault()) {
                    continue;
                }
                String id = dao.getName() + "." + method.getName();
                if (!configuration.hasStatement(id, false)) {
                    missing.add(id);
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "以下 Dao 方法找不到对应的 MyBatis 语句（XML 漏了 / namespace 写错 / 方法名对不上）：\n  "
                        + String.join("\n  ", missing));
    }
}
