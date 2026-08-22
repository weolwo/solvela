import java.sql.*;
import java.nio.file.*;
import java.util.*;

/**
 * 检查「分域 DDL 文件」与「权威基线」有没有漂移。
 *
 * <p><b>为什么需要它</b>：2026-08-22 起 {@code schema-baseline.sql} 是权威定义，
 * 而 {@code activity.sql / lottery.sql / task.sql / member.sql / mall.sql} 保留为
 * <b>带设计注释的可读分域视图</b>（计划等模块全部完工后再合并成一个文件、删掉分域文件）。
 *
 * <p>这个中间态的风险是：同一张表的定义存在两处，改一处忘另一处会<b>静默漂移</b> ——
 * 一直到最后合并那天才发现，而那时已经分不清哪边才是对的。
 * 所以让机器随时能报出偏差。
 *
 * <p><b>比结构不比文本</b>：两边都建到临时库里，再比 information_schema。
 * 直接比 DDL 文本不可靠 —— MySQL 会省略可推断的 CHARACTER SET，
 * 语义完全相同的两张表文本却不同（本项目已经踩过一次，误报了 6 张表）。
 */
public class CheckModuleDrift {

    static final String BASE = """
            jdbc:mysql://127.0.0.1:3306/%s?useSSL=false\
            &serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&allowMultiQueries=true""";
    static final String ROOT = "D:/workspace/smart-admin/数据库SQL脚本/";

    static final String[] MODULE_FILES = {
            "activity.sql", "lottery.sql", "mall.sql", "member.sql", "mysql/task.sql"
    };

    record Col(String name, String type, String nullable, String def, String collation, String extra) {}
    record Idx(String name, String cols, boolean unique) {}

    public static void main(String[] a) throws Exception {
        build("_drift_module", MODULE_FILES);
        build("_drift_baseline", new String[]{"mysql/schema-baseline.sql"});

        try (Connection c = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s = c.createStatement()) {

            Map<String, List<Col>> mc = cols(s, "_drift_module"), bc = cols(s, "_drift_baseline");
            Map<String, List<Idx>> mi = idxs(s, "_drift_module"), bi = idxs(s, "_drift_baseline");

            Set<String> both = new TreeSet<>(mc.keySet());
            both.retainAll(bc.keySet());
            Set<String> onlyModule = new TreeSet<>(mc.keySet());
            onlyModule.removeAll(bc.keySet());

            System.out.println("分域文件定义 " + mc.size() + " 张，基线 " + bc.size() + " 张，两边都有 " + both.size() + " 张\n");

            List<String> drift = new ArrayList<>();
            for (String t : both) {
                boolean cDiff = !Objects.equals(mc.get(t), bc.get(t));
                boolean iDiff = !Objects.equals(mi.get(t), bi.get(t));
                if (cDiff || iDiff) {
                    drift.add(t);
                    System.out.println("🔴 " + t + (cDiff ? "  列不一致" : "") + (iDiff ? "  索引不一致" : ""));
                    if (cDiff) diffCols(mc.get(t), bc.get(t));
                    if (iDiff) {
                        System.out.println("     分域: " + mi.get(t));
                        System.out.println("     基线: " + bi.get(t));
                    }
                }
            }
            if (drift.isEmpty()) System.out.println("两边都有的表：全部一致 ✓");

            if (!onlyModule.isEmpty()) {
                System.out.println("\n分域文件里有、基线里没有的表（" + onlyModule.size() + " 张）：");
                System.out.println("   " + onlyModule);
                System.out.println("   —— 这些是<b>尚未部署</b>的新表。基线是从真库导出的，"
                        + "所以它们要等部署到库里、重新导出基线之后才会出现。属正常。");
            }

            System.out.println(drift.isEmpty() ? "\nRESULT: PASS —— 无漂移" : "\nRESULT: DRIFT —— " + drift);
            drop("_drift_module"); drop("_drift_baseline");
            if (!drift.isEmpty()) System.exit(1);
        }
    }

    static void diffCols(List<Col> m, List<Col> b) {
        Map<String, Col> mm = new LinkedHashMap<>(), bb = new LinkedHashMap<>();
        m.forEach(c -> mm.put(c.name(), c));
        b.forEach(c -> bb.put(c.name(), c));
        Set<String> all = new TreeSet<>(mm.keySet());
        all.addAll(bb.keySet());
        for (String n : all) {
            Col x = mm.get(n), y = bb.get(n);
            if (!Objects.equals(x, y)) {
                System.out.println("     列 " + n);
                System.out.println("       分域: " + x);
                System.out.println("       基线: " + y);
            }
        }
    }

    static void build(String db, String[] files) throws Exception {
        try (Connection c = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + db);
            s.execute("CREATE DATABASE " + db + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try (Connection c = DriverManager.getConnection(String.format(BASE, db), "root", "root");
             Statement s = c.createStatement()) {
            for (String f : files) {
                for (String st : split(ROOT + f)) {
                    if (st.toUpperCase().startsWith("SET NAMES")) continue;
                    // 分域文件里可能有跨库 INSERT（如 mall.sql 往 t_file_category 插分类），
                    // 建表之外的语句失败不影响结构比对
                    try { s.execute(st); } catch (SQLException ignore) { }
                }
            }
        }
    }

    static void drop(String db) throws Exception {
        try (Connection c = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + db);
        }
    }

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

    static Map<String, List<Col>> cols(Statement s, String schema) throws SQLException {
        String sql = """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                       COLLATION_NAME, EXTRA
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = '%s'
                 ORDER BY TABLE_NAME, ORDINAL_POSITION""".formatted(schema);
        Map<String, List<Col>> m = new TreeMap<>();
        try (ResultSet r = s.executeQuery(sql)) {
            while (r.next())
                m.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(new Col(
                        r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7)));
        }
        return m;
    }

    static Map<String, List<Idx>> idxs(Statement s, String schema) throws SQLException {
        String sql = """
                SELECT TABLE_NAME, INDEX_NAME,
                       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX),
                       MAX(NON_UNIQUE) = 0
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = '%s'
                 GROUP BY TABLE_NAME, INDEX_NAME
                 ORDER BY TABLE_NAME, INDEX_NAME""".formatted(schema);
        Map<String, List<Idx>> m = new TreeMap<>();
        try (ResultSet r = s.executeQuery(sql)) {
            while (r.next())
                m.computeIfAbsent(r.getString(1), k -> new ArrayList<>())
                 .add(new Idx(r.getString(2), r.getString(3), r.getBoolean(4)));
        }
        return m;
    }
}
