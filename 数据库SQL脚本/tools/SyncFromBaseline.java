import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 把分域文件里指定表的 CREATE TABLE 块，整块替换成基线里的定义。
 *
 * <p>手工逐条补列/补索引是在打地鼠 —— 补完一处又冒出下一处（nullable、default、
 * 索引顺序都可能差）。既然基线是权威且是机器导出的，就整块搬过来，
 * 只保留分域文件里 CREATE TABLE 之前的那段设计注释。
 */
public class SyncFromBaseline {

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

    static final String ROOT = sqlRoot() + "/";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: SyncFromBaseline <分域文件相对路径> <表名> [表名...]");
            return;
        }
        String rel = args[0];
        List<String> tables = Arrays.asList(args).subList(1, args.length);

        String baseline = Files.readString(Path.of(ROOT + "mysql/schema-baseline.sql"));
        Path p = Path.of(ROOT + rel);
        String mod = Files.readString(p);

        for (String t : tables) {
            String fromBase = extract(baseline, t);
            if (fromBase == null) { System.out.println("  !! 基线里没有 " + t); continue; }
            String inMod = extract(mod, t);
            if (inMod == null) { System.out.println("  !! " + rel + " 里没有 " + t); continue; }
            mod = mod.replace(inMod, fromBase);
            System.out.println("  " + t + " 已同步（" + inMod.split("\n").length
                    + " 行 -> " + fromBase.split("\n").length + " 行）");
        }
        Files.write(p, mod.getBytes(StandardCharsets.UTF_8));
        System.out.println("写回 " + rel);
    }

    /** 抠出 CREATE TABLE `t` ( ... ) ...; 整块（不含前面的注释）。 */
    static String extract(String s, String t) {
        Matcher m = Pattern.compile("CREATE TABLE\\s+`" + Pattern.quote(t) + "`\\s*\\(.*?\\n\\)[^;]*;",
                Pattern.DOTALL).matcher(s);
        return m.find() ? m.group() : null;
    }
}
