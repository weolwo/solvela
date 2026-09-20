import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * 导出「系统种子数据」——新环境跑完 schema-baseline.sql 之后还必须灌进去的那部分数据。
 *
 * <p><b>为什么需要它</b>：schema-baseline.sql 只有结构。而系统能不能跑起来还依赖一批
 * 配置类数据：没有 t_menu / t_role_menu 后台就是一片空白；没有 t_file_category 的内置分类，
 * 代码里按 code 引用的地方直接抛异常；没有 t_solvela_job 三个定时任务不会注册。
 * 这些数据原先分散在 sql-update-log 的 17 个版本文件里（光 t_menu 就横跨 8 个文件）。
 *
 * <p>把它们收敛成一个文件之后，「新环境部署」= schema-baseline.sql + data-baseline.sql，
 * sql-update-log 才真正变成纯历史归档。
 */
public class DumpSeedData {

    static final String URL = """
            jdbc:mysql://127.0.0.1:3306/solvela?useSSL=false\
            &serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai""";

    /**
     * 种子表 = 「代码依赖它才能跑」的配置数据。
     * 刻意<b>不含</b>任何业务/运营/日志数据（会员、活动、任务记录、流水、各类 log），
     * 那些是跑造数脚本或真实使用产生的，进基线只会让新环境一开张就带着别人的测试数据。
     */

    /**
     * 会员号发号序列的种子行。固定输出，不从库里 dump —— 理由见下面 SQL 注释。
     */
    static final String MEMBER_ID_SEQ_BLOCK = """

-- =====================================================================================
-- 会员号发号序列的唯一一行。
--
-- \uD83D\uDD34 这行是【必须】的，不是可选种子数据。t_member_id_seq 是单行表，发号靠
--    UPDATE ... WHERE id = 1 推进水位。这一行不存在时，UPDATE 匹配 0 行、
--    LAST_INSERT_ID() 返回 0，算出的号段是 [-1000, 0) —— 于是【第一个注册的
--    用户】就撞上 BusinessException「会员号已耗尽或序号非法：-1000」，注册全线失败。
--
-- \uD83D\uDD34 本块由 DumpSeedData 【固定输出】，不是从库里 dump 的：
--    开发库的 next_seq 是运行态水位（早就几万了），而基线要的是种子值 0。
--    历史上它是手工粘进基线的，于是每次重新导出都被丢掉一次。
--
--    next_seq=0：第一次批发得到号段 [0, 1000)，起始内部序号 0（MemberIdCodec 接受 [0,CAPACITY)）。
--    step=1000：与 schema 默认值一致，库里的值优先于应用配置。
-- =====================================================================================
DELETE FROM `t_member_id_seq`;
INSERT INTO `t_member_id_seq` (`id`, `next_seq`, `step`) VALUES (1, 0, 1000);
""";

    static final LinkedHashMap<String, String> SEED = new LinkedHashMap<>();
    static {
        SEED.put("t_menu",                  "菜单树。没有它后台登录进去是空白");
        SEED.put("t_role",                  "角色");
        SEED.put("t_role_menu",             "角色-菜单授权");
        SEED.put("t_role_employee",         "角色-员工");
        SEED.put("t_role_data_scope",       "角色数据范围");
        SEED.put("t_department",            "部门");
        SEED.put("t_position",              "职务");
        SEED.put("t_employee",              "员工账号。⚠️ 含 Argon2 密码哈希与手机号，见文件头提示");
        SEED.put("t_dict",                  "字典");
        SEED.put("t_dict_data",             "字典项");
        SEED.put("t_config",                "系统配置");
        SEED.put("t_serial_number",         "单号生成器定义");
        SEED.put("t_file_category",         "文件分类。代码按 category_code 引用，缺了直接抛异常");
        SEED.put("t_solvela_job",             "定时任务定义。缺了任务不会注册");
        SEED.put("t_task_event",            "任务事件定义（v3.47.0 灌入）");
        SEED.put("t_notice_type",           "公告类型");
        // 2026-09-15 补：通知模板是配置数据，不是业务数据。
        // 缺了不报错，只是每条通知都在 NotificationService 里落一行
        // 「模板未配置或已全部停用」的 error 日志，然后用户什么都收不到 ——
        // 与 t_file_category / t_task_event 是同一性质的东西。
        SEED.put("t_notification_template", "通知模板。缺了发不出任何站内信，而且不报错");
        // 2026-09-15 补：此前【不在清单里，却在基线文件里】——
        // 说明它是某次手工粘进去的，而本工具每次导出都会把它丢掉。
        // 邮箱验证码发不出去 = 新环境注册不了，与 t_file_category 同一性质。
        SEED.put("t_mail_template",         "邮件模板。缺了邮箱验证码发不出去");
        // 2026-09-15 补：券模板是配置数据。缺了发券链路查不到规则，
        // 发出去的券就回到「只有名字没有规则」那个状态。
        SEED.put("t_coupon_template",       "优惠券模板。缺了发券查不到规则");
        // 2026-09-18 补：会员等级是配置数据 —— 门槛、档数、等级名都在后台改，
        // 所以它不可能是枚举，也就必须跟着基线走。
        // 🔴 缺了之后【不报错】：MemberGradeResolver 对每一次判级打一条
        //    「一条启用中的等级配置都没有，全部按 0 级处理」的告警，然后所有人恒为 0 级。
        //    与 t_task_event / t_coupon_template 是同一性质的东西。
        SEED.put("t_member_grade",          "会员等级定义。缺了所有人恒为 0 级，且只有告警不报错");
        // 2026-09-20 补：权益从 t_member_grade.benefits 那一列拆出来独立成表。
        // 它是【纯展示】的，缺了不影响任何逻辑 —— 但等级页会空着，
        // 而「用户看不见自己在保什么」等于整套保级机制白做。
        SEED.put("t_grade_privilege",       "等级权益【纯展示】。缺了等级页空白，用户不知道自己在保什么");
        SEED.put("t_code_generator_config", "代码生成器配置（开发工具，可选）");
        SEED.put("t_table_column",          "列配置（开发工具，可选）");
    }

    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, "root", "root");
             Statement s = c.createStatement()) {

            StringBuilder out = new StringBuilder();
            out.append("""
-- ⚠️ 必须保留这一行，且必须在所有语句之前（连接字符集若是 latin1，中文会整片乱码）。
SET NAMES utf8mb4;

-- =====================================================================================
-- solvela 系统种子数据
--
-- 🔴 <b>新环境部署第 2 步</b>：先执行 schema-baseline.sql（建结构），再执行本文件（灌数据）。
--    两个文件跑完，系统就能登录并正常使用。
--
-- 【本文件<b>只有配置数据</b>，没有任何业务数据】
--   会员、活动、任务记录、积分流水、各类日志 —— 一律不在这里。
--   那些要么是真实使用产生的，要么跑 *造数*.sql 生成，进基线只会让新环境
--   一开张就带着别人的测试数据。
--
-- 【为什么需要这个文件】
--   schema-baseline.sql 只有结构。而系统能不能跑起来还依赖一批配置数据，
--   它们原先散落在 sql-update-log 的 17 个版本文件里 —— 光 t_menu 的增量插入
--   就横跨 v3.18 / v3.41 / v3.42 / v3.46 / v3.47 / v3.48 / v3.52 / v3.60 八个文件。
--   靠人按版本号顺序执行才能拼出完整菜单，漏一个就少一块功能入口，而且不报错。
--
-- ⚠️ <b>t_employee 含 Argon2 密码哈希与手机号</b>。上游 smart_admin_v3.sql 本来也带，
--    所以这不是新增的暴露面；但本文件如果要发到公开仓库/交付给外部，
--    先把非必要账号删掉，只留一个 admin。
--
-- 生成方式：数据库SQL脚本/tools/DumpSeedData.java
-- 生成时间：%s
-- =====================================================================================

SET FOREIGN_KEY_CHECKS = 0;

""".formatted(LocalDate.now()));

            int totalRows = 0;
            List<String> empty = new ArrayList<>();
            for (var e : SEED.entrySet()) {
                String t = e.getKey();
                List<String> values = new ArrayList<>();
                List<String> cols = new ArrayList<>();
                try (ResultSet r = s.executeQuery("SELECT * FROM `" + t + "`")) {
                    ResultSetMetaData m = r.getMetaData();
                    for (int i = 1; i <= m.getColumnCount(); i++) cols.add(m.getColumnName(i));
                    while (r.next()) {
                        StringBuilder v = new StringBuilder("(");
                        for (int i = 1; i <= m.getColumnCount(); i++) {
                            if (i > 1) v.append(", ");
                            v.append(lit(r, i, m.getColumnType(i)));
                        }
                        values.add(v.append(')').toString());
                    }
                } catch (SQLException ex) {
                    out.append("-- [跳过] ").append(t).append(" : ").append(ex.getMessage()).append('\n');
                    continue;
                }
                if (values.isEmpty()) { empty.add(t); continue; }

                out.append("\n-- ").append("-".repeat(83)).append('\n');
                out.append("-- ").append(t).append("  ").append(e.getValue())
                   .append("（").append(values.size()).append(" 行）\n");
                out.append("-- ").append("-".repeat(83)).append('\n');
                out.append("DELETE FROM `").append(t).append("`;\n");
                String prefix = "INSERT INTO `" + t + "` (`" + String.join("`, `", cols) + "`) VALUES\n";
                // 每 200 行一批，避免单条语句过大撞 max_allowed_packet
                for (int i = 0; i < values.size(); i += 200) {
                    List<String> batch = values.subList(i, Math.min(i + 200, values.size()));
                    out.append(prefix).append(String.join(",\n", batch)).append(";\n");
                }
                totalRows += values.size();
            }

            // -----------------------------------------------------------------
            // 🔴 t_member_id_seq 【刻意不进 SEED 清单】，而是在这里写死输出。
            //
            //    它是【运行态计数器】：开发库里 next_seq 早被 UPDATE 推到几万，
            //    dump 出来的话，新环境一开张就从那个水位开始发号。
            //    基线要的是【种子值】next_seq = 0，两者不是一回事。
            //
            //    此前这一块是有人手工粘进基线文件的，于是【每次重新导出都会被丢掉】——
            //    而丢掉的后果不是少一行数据，是第一个注册的用户直接失败（见下面的注释）。
            //    固化在工具里，就不再依赖谁记得粘回去。
            // -----------------------------------------------------------------
            out.append(MEMBER_ID_SEQ_BLOCK);

            out.append("\nSET FOREIGN_KEY_CHECKS = 1;\n");
            if (!empty.isEmpty()) {
                out.append("\n-- 以下种子表当前为空，未生成 INSERT：\n");
                for (String t : empty) out.append("--   ").append(t).append('\n');
            }

            Path p = Path.of("D:/workspace/solvela/数据库SQL脚本/mysql/data-baseline.sql");
            Files.write(p, out.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("generated " + p);
            System.out.println("  seed tables=" + (SEED.size() - empty.size()) + "  rows=" + totalRows
                    + "  empty=" + empty);
            System.out.println("  size=" + Files.size(p) / 1024 + " KB");
        }
    }

    /** 生成 SQL 字面量。二进制列走 0x 十六进制，避免转义地狱。 */
    static String lit(ResultSet r, int i, int type) throws SQLException {
        Object o = r.getObject(i);
        if (o == null || r.wasNull()) return "NULL";
        switch (type) {
            case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY: case Types.BLOB: {
                byte[] b = r.getBytes(i);
                StringBuilder sb = new StringBuilder("0x");
                for (byte x : b) sb.append(String.format("%02X", x));
                return sb.toString();
            }
            case Types.BIT: case Types.BOOLEAN:
                return r.getBoolean(i) ? "1" : "0";
            case Types.TINYINT: case Types.SMALLINT: case Types.INTEGER: case Types.BIGINT:
            case Types.DECIMAL: case Types.NUMERIC: case Types.FLOAT: case Types.DOUBLE: case Types.REAL:
                return r.getString(i);
            default:
                return "'" + esc(r.getString(i)) + "'";
        }
    }

    static String esc(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (char ch : v.toCharArray()) {
            switch (ch) {
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> sb.append("\\0");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
