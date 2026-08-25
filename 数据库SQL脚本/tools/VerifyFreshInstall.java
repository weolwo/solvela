import java.sql.*;
import java.nio.file.*;
import java.util.*;

/**
 * 模拟一次全新部署：空库 → schema-baseline.sql → data-baseline.sql，
 * 然后检查关键种子数据是否到位。
 *
 * <p>这是「sql-update-log 能不能删」的唯一判据：只有这套跑通，
 * 那 49 个版本文件才真正只剩历史价值。
 */
public class VerifyFreshInstall {
    static final String BASE = """
            jdbc:mysql://127.0.0.1:3306/%s?useSSL=false\
            &serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&allowMultiQueries=true""";
    static final String ROOT = "D:/workspace/solvela-admin/数据库SQL脚本/mysql/";
    static final String PROBE = "_fresh_probe";

    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + PROBE);
            s.execute("CREATE DATABASE " + PROBE + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        int totalFail = 0;
        try (Connection c = DriverManager.getConnection(String.format(BASE, PROBE), "root", "root");
             Statement s = c.createStatement()) {
            for (String f : new String[]{"schema-baseline.sql", "data-baseline.sql"}) {
                int ok = 0, fail = 0;
                for (String st : split(ROOT + f)) {
                    if (st.toUpperCase().startsWith("SET NAMES")) continue;
                    try { s.execute(st); ok++; }
                    catch (SQLException e) {
                        fail++;
                        System.out.println("[FAIL] " + f + " : " + e.getMessage());
                        System.out.println("       " + st.substring(0, Math.min(110, st.length())).replaceAll("\\s+", " "));
                    }
                }
                System.out.println(f + "  ->  ok=" + ok + "  fail=" + fail);
                totalFail += fail;
            }

            System.out.println("\n=== 关键种子数据到位情况 ===");
            String[][] checks = {
                {"t_menu",          "1",   "菜单：为 0 后台登录进去是空白"},
                {"t_role_menu",     "1",   "角色菜单授权：为 0 则任何角色都看不到菜单"},
                {"t_employee",      "1",   "员工账号：为 0 无法登录"},
                {"t_file_category", "4",   "文件分类：代码按 code 引用 COMMON/NOTICE/HELP_DOC/FEEDBACK"},
                {"t_solvela_job",     "1",   "定时任务定义：为 0 则任务不会注册"},
                {"t_dict_data",     "1",   "字典项"},
                {"t_task_event",    "1",   "任务事件定义"},
                {"t_serial_number", "1",   "单号生成器定义"},
            };
            boolean allOk = true;
            for (String[] chk : checks) {
                long n;
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM `" + chk[0] + "`")) { r.next(); n = r.getLong(1); }
                long min = Long.parseLong(chk[1]);
                boolean pass = n >= min;
                allOk &= pass;
                System.out.printf("   %-18s %5d 行  %s   %s%n", chk[0], n, pass ? "OK  " : "FAIL", chk[2]);
            }

            // 业务表必须是空的 —— 基线不该带别人的测试数据
            System.out.println("\n=== 业务表应当为空（基线不带业务数据）===");
            String[] biz = {"t_member", "t_member_wallet", "t_member_asset_transaction",
                            "t_task_record", "t_prize_log", "t_activity_config", "t_lottery_record"};
            for (String t : biz) {
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM `" + t + "`")) {
                    r.next(); long n = r.getLong(1);
                    System.out.printf("   %-30s %5d 行  %s%n", t, n, n == 0 ? "OK" : "!! 混进了业务数据");
                    allOk &= (n == 0);
                }
            }

            // 内置文件分类的 code 必须齐
            System.out.println("\n=== 内置文件分类 code ===");
            List<String> codes = new ArrayList<>();
            try (ResultSet r = s.executeQuery("SELECT category_code FROM t_file_category ORDER BY category_code")) {
                while (r.next()) codes.add(r.getString(1));
            }
            System.out.println("   " + codes);
            for (String need : new String[]{"COMMON", "NOTICE", "HELP_DOC", "FEEDBACK"}) {
                if (!codes.contains(need)) { System.out.println("   !! 缺少内置分类 " + need); allOk = false; }
            }

            System.out.println((totalFail == 0 && allOk) ? "\nRESULT: PASS —— 空库两个文件即可得到可用系统"
                                                         : "\nRESULT: FAIL");
        }

        try (Connection c = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE " + PROBE);
        }
    }

    /** 按行攒语句，行尾分号收口；不依赖文件是 LF 还是 CRLF。 */
    static List<String> split(String path) throws Exception {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : Files.readAllLines(Path.of(path))) {
            String line = raw.strip();
            if (line.startsWith("--") || line.isEmpty()) continue;
            cur.append(line).append('\n');
            if (line.endsWith(";")) { out.add(cur.toString().strip().replaceAll(";$", "")); cur.setLength(0); }
        }
        if (!cur.isEmpty()) out.add(cur.toString().strip());
        return out;
    }
}
