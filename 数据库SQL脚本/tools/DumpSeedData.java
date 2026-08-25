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

            out.append("\nSET FOREIGN_KEY_CHECKS = 1;\n");
            if (!empty.isEmpty()) {
                out.append("\n-- 以下种子表当前为空，未生成 INSERT：\n");
                for (String t : empty) out.append("--   ").append(t).append('\n');
            }

            Path p = Path.of("D:/workspace/solvela-admin/数据库SQL脚本/mysql/data-baseline.sql");
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
