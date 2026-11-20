import java.sql.*;
import java.nio.file.*;
import java.util.*;

/**
 * 在空库上执行基线，然后比对<b>实际结构</b>（information_schema）而不是 SHOW CREATE TABLE 文本。
 * 文本比对不可靠：MySQL 会省略可从上下文推断的 CHARACTER SET，导致语义相同的两张表文本不同。
 */
public class VerifyBaseline {

    /**
     * 从当前目录往上找仓库里的 {@code 数据库SQL脚本} 目录；找不到就直接失败。
     *
     * <p>🔴 以前这里写死 {@code D:/workspace/solvela/...}（当时那台 Windows 开发机的路径）。
     * 换到别的机器上，它不报错：Linux 把它当成相对路径，读写的是当前目录下一个叫 {@code D:} 的子目录，
     * 基线文件写进了没人看的地方。这几个工具是单文件运行的，不能互相 import，所以每个文件各带一份这个方法。
     */
    static Path sqlRoot() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path candidate = p.resolve("数据库SQL脚本");
            if (Files.isDirectory(candidate.resolve("mysql"))) {
                return candidate;
            }
            if (p.getFileName() != null && p.getFileName().toString().equals("数据库SQL脚本")
                    && Files.isDirectory(p.resolve("mysql"))) {
                return p;
            }
        }
        throw new IllegalStateException("从 " + Path.of("").toAbsolutePath()
                + " 往上找不到「数据库SQL脚本/mysql」目录：请在仓库里任一子目录下运行（README 里是在 数据库SQL脚本/tools 下）");
    }
    static final String BASE = """
            jdbc:mysql://127.0.0.1:3306/%s?useSSL=false\
            &serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai&allowMultiQueries=true""";

    record Col(String name, String type, String nullable, String def, String collation, String extra) {}
    record Idx(String name, String cols, boolean unique) {}

    public static void main(String[] a) throws Exception {
        String sql = Files.readString(Path.of(
                sqlRoot().resolve("mysql").resolve("schema-baseline.sql").toString()));
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

        try (Connection c = DriverManager.getConnection(String.format(BASE, "solvela"), "root", "root");
             Statement s = c.createStatement()) {

            Map<String, List<Col>> srcC = cols(s, "solvela"), dstC = cols(s, "_baseline_probe");
            Map<String, List<Idx>> srcI = idxs(s, "solvela"), dstI = idxs(s, "_baseline_probe");

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
