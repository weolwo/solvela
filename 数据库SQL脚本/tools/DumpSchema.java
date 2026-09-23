import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 从当前库导出「唯一权威的全量表结构」——新环境执行这一个文件即可建好所有表。
 *
 * <p>为什么要它：现状是 77 张表的建表语句散落在 6 个 schema 文件 + 49 个版本迁移文件里，
 * 其中 3 张只在迁移日志里、5 张<b>任何文件都没有</b>。
 * 「新环境要执行哪些 SQL」这个问题在今天是没有答案的。
 */
public class DumpSchema {

    static final String URL = """
            jdbc:mysql://127.0.0.1:3306/solvela?useSSL=false\
            &serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai""";

    /** 手工备份表等垃圾，不进基线 */
    static final List<String> EXCLUDE_PREFIX = List.of("t_menu_26", "t_menu_2608");

    /**
     * 🔴 备份表的<b>命名约定</b>：{@code <原表名>_dup_<日期>} / {@code _bak_} / {@code _backup_}。
     *
     * <p>按名字认，而不是每出现一张就往 {@link #EXCLUDE_PREFIX} 里加一条 ——
     * 那份清单只会越来越长，而且漏加一次的表现是它<b>静悄悄进了基线</b>，
     * 于是全新安装会凭空多出一张别人某天的备份表。
     *
     * <p>⚠️ 反过来说：<b>想让备份表不进基线，就得按这个格式起名</b>。
     */
    static final Pattern BACKUP_TABLE = Pattern.compile(".+_(dup|bak|backup)_\\d{6,8}$");

    /** 按业务域分组，让基线文件本身可读 */
    static final LinkedHashMap<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("系统底座（上游 Solvela）", List.of(
            "t_employee","t_department","t_position","t_role","t_role_employee","t_role_menu",
            "t_role_data_scope","t_menu","t_login_log","t_login_fail","t_password_log",
            "t_operate_log","t_data_tracer",
            "t_config","t_dict","t_dict_data",
            "t_solvela_job","t_solvela_job_log",
            "t_table_column","t_code_generator_config","t_mail_template"));
        // 2026-08-31 从本组移除（库里已无、全仓零代码引用，随功能一起下掉的）：
        //   t_change_log / t_heart_beat_record / t_serial_number / t_serial_number_record
        //   / t_reload_item / t_reload_result
        //
        // 整组「办公 / 内容」同期移除：t_notice* 4 张、t_help_doc* 4 张、
        //   t_feedback / t_message / t_oa_bank / t_oa_enterprise / t_oa_enterprise_employee
        //   / t_oa_invoice / t_goods / t_category —— 共 16 张，同样零引用。
        //   留着不会出错（本类按 all::contains 过滤），但会让人以为库里还有它们。
        GROUPS.put("文件 / 素材库", List.of("t_file","t_file_category","t_file_relation"));
        GROUPS.put("会员域", List.of(
            "t_member","t_member_verify","t_member_id_seq","t_member_login_log",
            // 2026-08-31 补：此前不在任何组里，导出时会掉进「未分类」
            "t_member_operation_limit",
            // 2026-09-07 新增。它【没有 member_id】——一台设备登多个号是要发现的信号，
            // 不是要建的约束。归在会员域是因为它的 Dao 和读者都在这儿，
            // 而且查询时几乎总是和 t_member_login_log.device_id 一起出现。
            "t_device",
            // 2026-09-18 会员等级。四张都归会员域：等级是【会员属性】，
            // 成长值也在会员域 —— 它不能放钱包，因为 <modules> 里 member 排在
            // ledger 之前，会员域物理上读不到钱包表。
            "t_member_grade","t_member_growth","t_member_growth_log","t_member_grade_log",
            "t_member_period_summary","t_grade_privilege",
            // 2026-09-23 权益域。归会员域而不是账务域：这两张表只描述
            // 「谁在哪个周期有资格领什么」，真正发出去的资产在 t_member_coupon /
            // 钱包里。配置与凭证在这儿，资产在那儿，边界就是 AssetGrantApi。
            "t_grade_entitlement","t_grade_entitlement_grant"));
        GROUPS.put("账务 / 履约", List.of(
            "t_member_wallet","t_member_asset_transaction","t_member_coupon",
            "t_physical_delivery","t_proposal_record","t_promotion_config",
            // 2026-09-08 补：此前不在任何组里，每次导出都掉进「未分类」。
            // 它是优惠配置的分组（结构见 schema-baseline.sql 里的 t_promotion_group），跟着 t_promotion_config 走
            "t_promotion_group",
            // 2026-09-15 券使用闭环阶段 1：规则住模板、核销走流水。
            // 与 t_member_coupon 同域，放一起
            "t_coupon_template", "t_coupon_write_off"));
        GROUPS.put("营销 - 活动与奖品", List.of(
            "t_activity_config","t_activity_display","t_prize_config","t_prize_log",
            "t_prize_pool_config","t_prize_pool_item","t_pool_prize_mapping",
            "t_draw_prize_log",
            // 2026-08-31 补：此前不在任何组里，导出时会掉进「未分类」
            "t_mq_message_log",
            // 2026-09-08 补：同上。抽奖玩法的配置表，与 t_draw_prize_log 同域
            "t_draw_config"));
            // 同期移除 t_prize_group（库里已无、零引用）。
            // t_prize_dispatch_outbox 也在本次删除 —— 它的实体与 Dao 从来没有代码读写。
        GROUPS.put("营销 - 任务", List.of(
            "t_task_template","t_task_config","t_task_record","t_task_record_flow",
            "t_task_event","t_task_prize_mapping"));
        GROUPS.put("营销 - 彩票", List.of(
            "t_lottery_config","t_lottery_issue","t_lottery_prize_rule",
            "t_lottery_record","t_lottery_number_pool"));
        GROUPS.put("脚本引擎", List.of("t_script","t_script_ref"));
        GROUPS.put("通知中心", List.of(
            // 2026-09-15 新增。两半存储模型完全不同，但同属一个域：
            //   定向通知是写扩散（一人一条），公告是读扩散（一条内容一行 + 一人一个游标）
            "t_notification_template", "t_member_notification", "t_member_notification_preference",
            "t_announcement", "t_member_announcement_cursor", "t_announcement_ack"));
        // 2026-09-15 券使用闭环阶段 1。券模板与核销流水跟着
        // t_member_coupon 走（都在账务域），所以归进「账务 / 履约」那一组 ——
        // 见下方。这里不单开一组，避免券的三张表散在两个地方。
        GROUPS.put("积分商城", List.of(
            "t_mall_category","t_mall_commodity","t_mall_sku","t_mall_order",
            "t_mall_exchange_limit","t_mall_address","t_mall_favorite"));
        // 2026-09-15 券使用闭环阶段 7：外部场景消费（充话费等）。
        // 单开一组而不是塞进「积分商城」—— 它和商城互不依赖，
        // 共同点只有「都用券」，归在一起会让人以为它们共享单据模型。
        GROUPS.put("外部场景消费", List.of("t_external_order"));
    }

    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, "root", "root");
             Statement s = c.createStatement()) {

            List<String> all = new ArrayList<>();
            try (ResultSet r = s.executeQuery("""
                    SELECT TABLE_NAME
                      FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = 'solvela' AND TABLE_TYPE = 'BASE TABLE'
                     ORDER BY TABLE_NAME""")) {
                while (r.next()) all.add(r.getString(1));
            }
            List<String> excluded = new ArrayList<>();
            all.removeIf(t -> {
                boolean bad = EXCLUDE_PREFIX.stream().anyMatch(t::startsWith)
                        || BACKUP_TABLE.matcher(t).matches();
                if (bad) excluded.add(t);
                return bad;
            });

            // 分组归属；没归到组的落「未分类」
            Set<String> assigned = new LinkedHashSet<>();
            GROUPS.values().forEach(assigned::addAll);
            List<String> unclassified = new ArrayList<>(all);
            unclassified.removeAll(assigned);

            StringBuilder out = new StringBuilder();
            out.append("""
-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文注释会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- solvela 全量表结构基线（只有结构，没有数据）
--
-- 🔴 <b>新环境部署：执行本文件即可，不需要再翻 sql-update-log。</b>
--
-- 生成方式：从开发库用 SHOW CREATE TABLE 逐表导出（见 scratchpad/DumpSchema.java），
--          所以它<b>就是库里真实的样子</b>，不是人工维护的近似版本。
--
-- 【为什么要有这个文件】
--   在它出现之前，77 张表的建表语句散落在 6 个 schema 文件 + 49 个版本迁移文件里：
--     · 3 张只存在于迁移日志（t_activity_display / t_file_category / t_file_relation）
--     · 5 张<b>任何文件里都找不到建表语句</b>（t_task_event / t_task_record_flow /
--       t_lottery_number_pool + 两张手工备份表）
--   也就是说「新环境要执行哪些 SQL」这个问题，在今天是<b>没有答案</b>的 ——
--   照 mysql/README.md 说的只跑 smart_admin_v3.sql，会缺掉整个营销域和会员域。
--   这也是铁律 22 的根因：表清单靠 grep 文件永远是不全的。
--
-- 【维护约定（重要，不遵守它这个文件三个月后就又失真了）】
--   改表结构时<b>两个地方都要动</b>：
--     ① 本文件 —— 让新环境建出来就是最新的
--     ② mysql/sql-update-log/vX.sql —— 让已有环境能升上来
--   然后重新跑一次 DumpSchema 覆盖本文件，用 git diff 核对是否与预期一致。
--   🔴 只改迁移不改基线 = 新环境和老环境结构不一样，而且没人会发现。
--
-- 🔴 <b>不要手改本文件。</b>改表结构 → 写迁移 sql → 在开发库执行 → 重跑 DumpSchema。
--    2026-08-23 那一版被人手工改过而没有重新导出，结果是头部写 84 张、README 写 75 张、
--    实际 64 张，三个数互不相同，而没有任何人能一眼看出它还准不准。
--    这个文件的全部价值就是「它就是库里真实的样子」——手改一次，它就退化成
--    一份人工维护的近似版本，也就是它当初要取代的那个东西。
--
--    ⚠️ 这段话写在 DumpSchema 的模板里，不写在本文件里 ——
--    写在这里的任何字，下一次导出都会被冲掉（2026-09-08 就冲掉过一段人工核对记录）。
--
-- 生成时间：%s
-- 表数量：%d 张
-- =====================================================================================

""".formatted(LocalDate.now(), all.size()));

            if (!excluded.isEmpty()) {
                out.append("-- 刻意排除（手工备份表，不属于系统结构）：\n");
                for (String t : excluded) out.append("--   ").append(t).append('\n');
                out.append('\n');
            }

            LinkedHashMap<String, List<String>> render = new LinkedHashMap<>(GROUPS);
            if (!unclassified.isEmpty()) render.put("未分类（新表？请归组）", unclassified);

            int written = 0;
            Set<String> done = new HashSet<>();
            for (var g : render.entrySet()) {
                List<String> present = g.getValue().stream().filter(all::contains).toList();
                if (present.isEmpty()) continue;
                out.append("\n-- ").append("=".repeat(85)).append('\n');
                out.append("-- ").append(g.getKey()).append("（").append(present.size()).append(" 张）\n");
                out.append("-- ").append("=".repeat(85)).append("\n\n");
                for (String t : present) {
                    try (ResultSet r = s.executeQuery("SHOW CREATE TABLE `" + t + "`")) {
                        r.next();
                        String ddl = r.getString(2);
                        // 去掉 AUTO_INCREMENT=nnn：那是当前库的运行时状态，不该进结构基线
                        ddl = ddl.replaceAll("\\s+AUTO_INCREMENT=\\d+", "");
                        out.append("DROP TABLE IF EXISTS `").append(t).append("`;\n");
                        out.append(ddl).append(";\n\n");
                        written++; done.add(t);
                    }
                }
            }

            List<String> missed = new ArrayList<>(all);
            missed.removeAll(done);
            if (!missed.isEmpty()) {
                System.out.println("!! 有表没被写出: " + missed);
                System.exit(1);
            }

            Path p = Path.of("D:/workspace/solvela/数据库SQL脚本/mysql/schema-baseline.sql");
            Files.write(p, out.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("已生成 " + p);
            System.out.println("  表数量 " + written + " 张，排除 " + excluded.size() + " 张备份表: " + excluded);
            System.out.println("  未分类 " + unclassified.size() + " 张" + (unclassified.isEmpty() ? "" : ": " + unclassified));
            System.out.println("  文件大小 " + Files.size(p) / 1024 + " KB");
        }
    }
}
