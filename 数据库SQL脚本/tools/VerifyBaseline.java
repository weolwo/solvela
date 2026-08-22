import java.sql.*;
import java.nio.file.*;
import java.util.*;

/**
 * 在空库上执行基线，然后比对<b>实际结构</b>（information_schema）而不是 SHOW CREATE TABLE 文本。
 * 文本比对不可靠：MySQL 会省略可从上下文推断的 CHARACTER SET，导致语义相同的两张表文本不同。
 */
public class VerifyBaseline2 {
    static final String BASE = "jdbc:mysql://127.0.0.1:3306/%s?useSSL=false"
            + "&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&allowMultiQueries=true";

    record Col(String name, String type, String nullable, String def, String collation, String extra) {}
    record Idx(String name, String cols, boolean unique) {}

    public static void main(String[] a) throws Exception {
        String sql = Files.readString(Path.of(
                "D:/workspace/smart-admin/数据库SQL脚本/mysql/schema-baseline.sql"));
        List<String> stmts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : sql.split("\n")) {
            String line = raw.strip();
            if (line.startsWith("--") || line.isEmpty()) continue;
            cur.append(line).append('\n');
            if (line.endsWith(";")) { stmts.add(cur.toString().strip().replaceAll(";$", "")); cur.setLength(0); }
        }

        try (Connection r0 = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s0 = r0.createStatement()) {
            s0.execute("DROP DATABASE IF EXISTS _baseline_probe");
            s0.execute("CREATE DATABASE _baseline_probe DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        int ok = 0, fail = 0;
        try (Connection c = DriverManager.getConnection(String.format(BASE, "_baseline_probe"), "root", "root");
             Statement s = c.createStatement()) {
            for (String st : stmts) {
                if (st.toUpperCase().startsWith("SET NAMES")) continue;
                try { s.execute(st); ok++; }
                catch (SQLException e) { fail++; System.out.println("[FAIL] " + e.getMessage()); }
            }
        }
        System.out.println("baseline on empty db: ok=" + ok + " fail=" + fail);

        try (Connection c = DriverManager.getConnection(String.format(BASE, "smart_admin_v3"), "root", "root");
             Statement s = c.createStatement()) {

            Map<String, List<Col>> srcC = cols(s, "smart_admin_v3"), dstC = cols(s, "_baseline_probe");
            Map<String, List<Idx>> srcI = idxs(s, "smart_admin_v3"), dstI = idxs(s, "_baseline_probe");

            List<String> tabs = new ArrayList<>(dstC.keySet());
            Collections.sort(tabs);
            int colSame = 0, idxSame = 0;
            List<String> colDiff = new ArrayList<>(), idxDiff = new ArrayList<>();
            for (String t : tabs) {
                if (Objects.equals(srcC.get(t), dstC.get(t))) colSame++; else colDiff.add(t);
                if (Objects.equals(srcI.get(t), dstI.get(t))) idxSame++; else idxDiff.add(t);
            }
            System.out.println("tables built: " + tabs.size());
            System.out.println("columns identical: " + colSame + " / " + tabs.size()
                    + (colDiff.isEmpty() ? "" : "  DIFF=" + colDiff));
            System.out.println("indexes identical: " + idxSame + " / " + tabs.size()
                    + (idxDiff.isEmpty() ? "" : "  DIFF=" + idxDiff));

            for (String t : colDiff.subList(0, Math.min(2, colDiff.size()))) {
                System.out.println("\n--- " + t + " column diff ---");
                List<Col> x = srcC.get(t), y = dstC.get(t);
                for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
                    Col cx = i < x.size() ? x.get(i) : null, cy = i < y.size() ? y.get(i) : null;
                    if (!Objects.equals(cx, cy)) { System.out.println("  src: " + cx); System.out.println("  dst: " + cy); }
                }
            }

            List<String> srcTabs = new ArrayList<>(srcC.keySet());
            srcTabs.removeAll(tabs);
            System.out.println("\nin source but not in baseline: " + srcTabs);
            boolean pass = fail == 0 && colDiff.isEmpty() && idxDiff.isEmpty();
            System.out.println(pass ? "\nRESULT: PASS" : "\nRESULT: FAIL");
        }

        try (Connection r0 = DriverManager.getConnection(String.format(BASE, ""), "root", "root");
             Statement s0 = r0.createStatement()) {
            s0.execute("DROP DATABASE _baseline_probe");
        }
    }

    static Map<String, List<Col>> cols(Statement s, String schema) throws SQLException {
        Map<String, List<Col>> m = new TreeMap<>();
        try (ResultSet r = s.executeQuery(
                "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, "
              + "COLLATION_NAME, EXTRA FROM information_schema.COLUMNS "
              + "WHERE TABLE_SCHEMA='" + schema + "' ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
            while (r.next())
                m.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(new Col(
                        r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7)));
        }
        return m;
    }

    static Map<String, List<Idx>> idxs(Statement s, String schema) throws SQLException {
        Map<String, List<Idx>> m = new TreeMap<>();
        try (ResultSet r = s.executeQuery(
                "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX), "
              + "MAX(NON_UNIQUE)=0 FROM information_schema.STATISTICS "
              + "WHERE TABLE_SCHEMA='" + schema + "' GROUP BY TABLE_NAME, INDEX_NAME "
              + "ORDER BY TABLE_NAME, INDEX_NAME")) {
            while (r.next())
                m.computeIfAbsent(r.getString(1), k -> new ArrayList<>())
                 .add(new Idx(r.getString(2), r.getString(3), r.getBoolean(4)));
        }
        return m;
    }
}
