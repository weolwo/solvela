package sa.base.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import sa.base.common.constant.TenantConst;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 启动自检：{@link TenantConst#TENANT_TABLES} 这份手工清单，和库里真实带
 * {@code tenant_id} 的表，必须一一对上。
 *
 * <p>🔴 <b>为什么必须有它</b>（交接文档铁律 22）：本项目的建表语句分散在主 schema 文件
 * 和 49 个历史迁移文件两处，靠 grep 文件维护表清单<b>已经漏过两次</b>，
 * 两次都是 {@code t_task_record_flow}：
 * <ul>
 *   <li>租户默认值改造：按文件找到 22 张，实际 25 张，漏的那张有 2933 行会被留在旧值</li>
 *   <li>关联键迁移：按文件找到 9 张，实际 10 张</li>
 * </ul>
 * 人不可能记得住这件事，所以让机器每次启动都对一遍。
 *
 * <p>两类偏差的后果完全不同，所以分开报：
 * <ul>
 *   <li><b>库里有、清单里没有</b> —— 这张表<b>不会被隔离</b>，是跨租户数据泄露的口子。ERROR。</li>
 *   <li><b>清单里有、库里没有</b> —— 拦截器会给它塞一个不存在的列，那张表一查就报
 *       {@code Unknown column 'tenant_id'}。ERROR。</li>
 * </ul>
 *
 * <p>刻意<b>只告警不阻断启动</b>：清单偏差不该让整个服务起不来，
 * 但日志里必须刺眼到不可能被忽略。
 *
 * @Date 2026-08-22
 */
@Slf4j
@Component
public class TenantTableSelfCheck {

    private final JdbcTemplate jdbcTemplate;

    public TenantTableSelfCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        List<String> actual;
        try {
            actual = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM information_schema.COLUMNS "
                  + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = ?",
                    String.class, TenantConst.TENANT_COLUMN);
        } catch (Exception e) {
            log.warn("[租户自检] 查询 information_schema 失败，跳过本次自检", e);
            return;
        }

        Set<String> inDb = new TreeSet<>();
        actual.forEach(t -> inDb.add(t.toLowerCase()));
        Set<String> inList = new TreeSet<>(TenantConst.TENANT_TABLES);

        Set<String> missingFromList = new TreeSet<>(inDb);
        missingFromList.removeAll(inList);
        Set<String> missingFromDb = new TreeSet<>(inList);
        missingFromDb.removeAll(inDb);

        if (missingFromList.isEmpty() && missingFromDb.isEmpty()) {
            log.info("[租户自检] 通过：{} 张表按租户隔离，与库中实际一致", inList.size());
            return;
        }

        log.error("========================= 🔴 租户表清单对不上 =========================");
        if (!missingFromList.isEmpty()) {
            log.error("[租户自检] 库里有 tenant_id、但 TenantConst.TENANT_TABLES 里没有 -> "
                    + "这些表【不会被租户隔离】，是跨租户数据泄露的口子：{}", missingFromList);
        }
        if (!missingFromDb.isEmpty()) {
            log.error("[租户自检] 清单里有、但库里那张表没有 tenant_id 列 -> "
                    + "拦截器会给它塞不存在的列，一查就报 Unknown column：{}", missingFromDb);
        }
        log.error("[租户自检] 修法：改 TenantConst.TENANT_TABLES，或给表补 tenant_id 列。"
                + "两边对齐之前，上面列出的表处于不确定状态。");
        log.error("=====================================================================");
    }
}
