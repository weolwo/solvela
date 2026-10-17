import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * 网关阶梯限流与安全闸的端到端验收（网关方案 §10 任务 T15）。<b>打的是本机真跑着的三个进程</b>：
 * 网关 :1025、app-biz :1026、后台 :1024（后台里的定时任务负责事件落库与名单维护），连真 MySQL、真 Redis。
 *
 * <pre>
 * java -cp ~/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar solvela-api/e2e/GatewaySecurityE2E.java
 * </pre>
 *
 * <h3>它怎么造数据</h3>
 * <ul>
 *   <li>客户端 IP 用 {@code X-Forwarded-For} 冒充（本机直连网关，没有 nginx 在前面覆盖它），取 198.18.0.0/15 测试地址段；</li>
 *   <li>要登录的场景（签到、兑换、会员灰名单）：在库里建一个测试会员，往 Redis 里铸一张会话（与 {@code MemberRedisTokenStore} 同格式）；</li>
 *   <li>阶梯「每一档」不能真等 8 小时：每罚一档，就把那条处罚的 {@code until} 改到过去（与 RateLimiterTest 同一个办法）；</li>
 *   <li>「黑到期转灰」把那条自动黑名单的到期改到过去，然后<b>等后台的名单维护任务真跑一次</b>（每 5 分钟，所以整套要跑 6 分钟左右）。</li>
 * </ul>
 *
 * <h3>⚠️ 它不登录后台</h3>
 * 本地没有可用的测试管理员账号。「后台解除处罚」按解除接口实际做的那件事来验（删处罚键，见 {@code PenaltyAdminService.release}），
 * 后台接口本身由 {@code SecurityCenterAdminTest} 覆盖。
 *
 * <p>退出码：全部通过为 0，有失败为 1。
 *
 * @Date 2026-10-17
 */
public class GatewaySecurityE2E {

    static final String GATEWAY = env("E2E_GATEWAY", "http://127.0.0.1:1025");
    static final String JDBC = env("E2E_JDBC", "jdbc:mysql://127.0.0.1:3306/solvela?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Shanghai&characterEncoding=utf8");
    static final String DB_USER = env("E2E_DB_USER", "root");
    static final String DB_PASSWORD = env("E2E_DB_PASSWORD", "root");
    static final int REDIS_DB = 1;

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final List<String[]> RESULTS = new ArrayList<>();
    static final ThreadLocalRandom R = ThreadLocalRandom.current();
    static Connection db;
    static Redis redis;

    public static void main(String[] args) throws Exception {
        db = DriverManager.getConnection(JDBC, DB_USER, DB_PASSWORD);
        redis = new Redis("127.0.0.1", 6379, REDIS_DB);
        // 上一轮最后的熔断用例会让蜜罐在 10 分钟内只记录不罚：开跑前清掉本机的蜜罐熔断计数
        redis.cmd("DEL", "sec:hp:breaker");
        Map<String, int[]> thresholds = thresholds();
        long member = createMember();
        String token = mintSession(member);
        try {
            System.out.println("== 一、每个场景撞阈值");
            scenes(thresholds, token);
            System.out.println("== 二、阶梯每一档");
            ladder();
            System.out.println("== 三、蜜罐与它的保险");
            String honeypotIp = honeypots();
            System.out.println("== 四、关键字与它的保险");
            String keywordIp = keywords();
            System.out.println("== 五、后台解除后立刻可用");
            release();
            System.out.println("== 六、事件落库、自动拉黑、被挡次数、黑到期转灰、同段连坐（等后台定时任务）");
            listsAndJobs(honeypotIp, keywordIp);
            System.out.println("== 七、会员灰名单（登录后场景账号维度罚到第 4 档 → 自动拉灰 → 网关阈值减半）");
            memberGrey(member, token, thresholds);
            System.out.println("== 八、蜜罐熔断（放最后：熔断窗口 10 分钟内会影响别的蜜罐用例）");
            breaker();
        } finally {
            cleanUp(member);
        }
        long failed = RESULTS.stream().filter(r -> r[0].equals("❌")).count();
        System.out.println();
        System.out.println("== 汇总：" + (RESULTS.size() - failed) + " 项通过，" + failed + " 项失败");
        RESULTS.stream().filter(r -> r[0].equals("❌")).forEach(r -> System.out.println("   ❌ " + r[1] + " —— " + r[2]));
        System.exit(failed == 0 ? 0 : 1);
    }

    // ================================================================== 一、场景

    static void scenes(Map<String, int[]> t, String token) throws Exception {
        record Case(String scene, int dim, String method, String path, boolean login, java.util.function.Supplier<String> body) {
        }
        List<Case> cases = List.of(
                new Case("LOGIN", 1, "POST", "/auth/login", false,
                        () -> "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email() + "\",\"credential\":\"x\"}"),
                new Case("REGISTER", 1, "POST", "/auth/register", false,
                        () -> "{\"registerType\":\"EMAIL_CODE\",\"identity\":\"" + email() + "\",\"emailCode\":\"000000\"}"),
                new Case("CODE_SEND", 1, "POST", "/auth/email/code", false,
                        () -> "{\"scene\":\"LOGIN\",\"email\":\"" + email() + "\"}"),
                new Case("PASSWORD_RESET", 1, "POST", "/auth/password/reset", false,
                        () -> "{\"identity\":\"" + email() + "\",\"code\":\"000000\",\"newPassword\":\"E2eNew2026!\"}"),
                new Case("CAPTCHA", 1, "POST", "/captcha", false, () -> "{}"),
                new Case("DEFAULT_READ", 1, "GET", "/activity", false, () -> null),
                new Case("SIGN", 2, "POST", "/task/sign", true, () -> "{}"),
                new Case("REDEEM", 2, "POST", "/entitlement/999999999/claim", true, () -> "{}"),
                new Case("DEFAULT_WRITE", 2, "POST", "/address", true, () -> "{}"));
        for (Case c : cases) {
            int burst = t.get(c.scene() + ":" + c.dim())[0];
            String ip = ip();
            if (c.login()) {
                resetMemberCounters(token, c.scene());
            }
            int firstLimited = -1;
            Resp limited = null;
            for (int i = 1; i <= burst + 3 && firstLimited < 0; i++) {
                Resp r = send(c.method(), c.path(), c.login() ? ip() : ip, c.body().get(), c.login() ? auth(token) : Map.of());
                if (r.status == 429) {
                    firstLimited = i;
                    limited = r;
                }
            }
            String dimName = c.dim() == 1 ? "IP" : "账号";
            // 令牌桶边用边补：请求要走下游、发得慢时，发到第 burst+1 次之前可能已经补回一两个令牌
            check(c.scene() + " 场景" + dimName + "维度：突发 " + burst + " 用完之后 429 + Retry-After",
                    firstLimited >= burst + 1 && firstLimited <= burst + 3 && limited.retryAfter != null
                            && limited.body.contains("OPERATION_LIMITED"),
                    "第一次 429 出现在第 " + firstLimited + " 次，Retry-After=" + (limited == null ? null : limited.retryAfter));
        }
        // 匿名场景的账号维度：同一个邮箱换着 IP 打登录
        String target = email();
        int burst = t.get("LOGIN:2")[0];
        int firstLimited = -1;
        for (int i = 1; i <= burst + 3 && firstLimited < 0; i++) {
            Resp r = send("POST", "/auth/login", ip(), "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + target
                    + "\",\"credential\":\"x\"}", Map.of());
            if (r.status == 429) {
                firstLimited = i;
            }
        }
        check("LOGIN 场景账号维度（匿名，账号在请求体里）：同一个邮箱换着 IP 打，突发 " + burst + " 用完之后 429",
                firstLimited >= burst + 1 && firstLimited <= burst + 3, "第一次 429 出现在第 " + firstLimited + " 次");
    }

    // ================================================================== 二、阶梯

    static void ladder() throws Exception {
        long[] expected = {60, 300, 1800, 3600, 7200, 28800, 28800};
        String ip = ip();
        String key = "rl:p:LOGIN:1:" + ip;
        List<Long> got = new ArrayList<>();
        for (int level = 0; level < expected.length; level++) {
            Long penalty = null;
            for (int i = 0; i < 40 && penalty == null; i++) {
                Resp r = send("POST", "/auth/login", ip, "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email()
                        + "\",\"credential\":\"x\"}", Map.of());
                if (r.status == 429 && r.retryAfter != null && r.retryAfter > 30) {
                    penalty = r.retryAfter;
                }
            }
            got.add(penalty);
            // 不真等：把这一档的到期改到过去，下一次违规就是下一档
            redis.cmd("HSET", key, "until", String.valueOf(now() - 1));
        }
        boolean ok = true;
        for (int i = 0; i < expected.length; i++) {
            ok &= got.get(i) != null && Math.abs(got.get(i) - expected[i]) <= 2;
        }
        check("阶梯：60 秒 → 5 分钟 → 30 分钟 → 1 小时 → 2 小时 → 8 小时，之后停在 8 小时", ok, "实际 " + got);
    }

    // ================================================================== 三、蜜罐

    static String honeypots() throws Exception {
        String ip = ip();
        Resp honeypot = send("GET", "/.env", ip, null, Map.of());
        Resp missing = send("GET", "/e2e-no-such-" + UUID.randomUUID(), ip(), null, Map.of());
        check("蜜罐 /.env 的响应与任何一个不存在的路径相同（除了链路 id）",
                honeypot.status == missing.status && stripTrace(honeypot.body).equals(stripTrace(missing.body)),
                honeypot.status + " " + honeypot.body + " vs " + missing.status + " " + missing.body);
        Resp next = send("GET", "/activity", ip, null, Map.of());
        check("碰了蜜罐的 IP，下一个请求（任意接口）在安全闸 429", next.status == 429 && next.retryAfter != null,
                next.status + " Retry-After=" + next.retryAfter);

        String crossSite = ip();
        send("GET", "/.env", crossSite, null, Map.of("Sec-Fetch-Site", "cross-site", "Origin", "https://evil.example"));
        Resp after = send("GET", "/activity", crossSite, null, Map.of());
        check("第 1 道保险：跨站发起的蜜罐命中不罚", after.status != 429, "下一个请求 " + after.status);
        check("第 3 道保险：蜜罐与真实接口重叠 → 启动失败（单测 SecurityGateFilterTest.overlapWithRealRoutes 覆盖，这里只确认网关正常起着）",
                send("GET", "/activity", ip(), null, Map.of()).status == 200, "");
        return ip;
    }

    // ================================================================== 四、关键字

    static String keywords() throws Exception {
        String ip = ip();
        Resp probe = send("GET", "/activity?keyword=1%20union%20select%20null,2", ip, null, Map.of());
        Resp malformed = send("POST", "/auth/login", ip(), "{", Map.of());
        check("关键字命中 → 400，与参数校验失败逐字相同（除了链路 id）",
                probe.status == 400 && stripTrace(probe.body).equals(stripTrace(malformed.body)),
                probe.status + " " + probe.body + " vs " + malformed.body);
        check("关键字命中之后，这个 IP 的下一个请求 429", send("GET", "/activity", ip, null, Map.of()).status == 429, "");

        String crossSite = ip();
        Resp cs = send("GET", "/activity?keyword=1%20union%20select%20null,2", crossSite, null,
                Map.of("Sec-Fetch-Site", "cross-site"));
        check("跨站发起的关键字命中：这一次照样拒绝（400），但不罚", cs.status == 400
                && send("GET", "/activity", crossSite, null, Map.of()).status == 200, "");

        String pwdIp = ip();
        Resp pwd = send("POST", "/auth/login", pwdIp, "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email()
                + "\",\"credential\":\"Color(2024)' or 1=1\"}", Map.of());
        check("密码字段里写什么都不触发关键字（不是 400，下一个请求也不 429）",
                pwd.status != 400 && send("GET", "/activity", pwdIp, null, Map.of()).status != 429, "登录回 " + pwd.status);

        String uaIp = ip();
        Resp ua = send("GET", "/activity", uaIp, null, Map.of("User-Agent", "sqlmap/1.8#stable"));
        check("扫描器 UA → 400 并罚", ua.status == 400 && send("GET", "/activity", uaIp, null, Map.of()).status == 429, "");
        return ip;
    }

    // ================================================================== 五、解除

    static void release() throws Exception {
        String ip = ip();
        Long penalty = null;
        for (int i = 0; i < 40 && penalty == null; i++) {
            Resp r = send("POST", "/auth/login", ip, "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email()
                    + "\",\"credential\":\"x\"}", Map.of());
            if (r.status == 429 && r.retryAfter != null && r.retryAfter > 30) {
                penalty = r.retryAfter;
            }
        }
        redis.cmd("DEL", "rl:p:LOGIN:1:" + ip);
        redis.cmd("DEL", "rl:c:LOGIN:1:" + ip);
        Resp after = send("POST", "/auth/login", ip, "{\"loginType\":\"EMAIL_PASSWORD\",\"identity\":\"" + email()
                + "\",\"credential\":\"x\"}", Map.of());
        check("限流处罚解除后立刻可用（解除 = 删处罚键）", penalty != null && after.status != 429, "解除后 " + after.status);

        String gateIp = ip();
        send("GET", "/.env", gateIp, null, Map.of());
        boolean blocked = send("GET", "/activity", gateIp, null, Map.of()).status == 429;
        redis.cmd("DEL", "sec:pen:ip:" + gateIp);
        Thread.sleep(5500); // 网关进程内缓存最多再挡 5 秒
        Resp after2 = send("GET", "/activity", gateIp, null, Map.of());
        check("蜜罐处罚解除后几秒内可用（进程内缓存 5 秒）", blocked && after2.status == 200, "解除后 " + after2.status);
    }

    // ================================================================== 六、事件与名单

    static void listsAndJobs(String honeypotIp, String keywordIp) throws Exception {
        String prefix = "198.19." + R.nextInt(256) + ".";
        for (int i = 1; i <= 3; i++) {
            send("GET", "/.env", prefix + i, null, Map.of());
        }
        // 封禁期间被挡的请求：只在网关里攒数，10 秒刷一次 Redis，名单维护任务再同步进库
        for (int i = 0; i < 5; i++) {
            send("GET", "/activity", honeypotIp, null, Map.of());
        }

        boolean incidents = waitFor(Duration.ofSeconds(100), () ->
                count("SELECT COUNT(*) FROM t_security_incident WHERE client_ip = ? AND incident_type = 1", honeypotIp) > 0
                        && count("SELECT COUNT(*) FROM t_security_incident WHERE client_ip = ? AND incident_type = 3", keywordIp) > 0);
        check("事件落库（落库任务每分钟一次）：蜜罐、关键字各一行", incidents, "");
        boolean listed = waitFor(Duration.ofSeconds(30), () ->
                count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 1 AND source = 2 AND source_rule = 'HONEYPOT' AND status = 1", honeypotIp) == 1
                        && count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 1 AND source_rule = 'KEYWORD'", keywordIp) == 1);
        check("自动拉黑：蜜罐 / 关键字命中的 IP 进了黑名单（自动、到期 = 处罚到期）", listed, "");
        long snapshotHas = waitFor(Duration.ofSeconds(30), () -> {
            Object body = redis.cmd("HGET", "gw:rl:snapshot", "body");
            return body != null && body.toString().contains("\"" + honeypotIp + "\"");
        }) ? 1 : 0;
        check("名单变了立即重发网关快照：快照里有这个 IP", snapshotHas == 1, "");

        // 黑到期转灰：把它的到期改到过去，等名单维护任务（每 5 分钟）
        update("UPDATE t_security_list SET expire_time = NOW() - INTERVAL 1 MINUTE WHERE list_value = ? AND list_type = 1", honeypotIp);
        System.out.println("   …… 等后台的名单维护任务跑一次（每 5 分钟，最多等 6 分钟）");
        boolean escalated = waitFor(Duration.ofMinutes(6), () ->
                count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 2 AND source_rule = 'ESCALATE' AND status = 1", honeypotIp) == 1);
        check("黑到期转灰：同一个 IP 进灰名单 30 天，黑的那行撤销", escalated
                && count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 1 AND status = 0", honeypotIp) == 1, "");
        check("同段连坐：同一 /24 里 3 个 IP 被自动拉黑 → 整段拉灰（只拉灰）",
                count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 2 AND source_rule = 'SEGMENT'", prefix + "0/24") == 1
                        && count("SELECT COUNT(*) FROM t_security_list WHERE list_value = ? AND list_type = 1", prefix + "0/24") == 0, "");
        check("被挡次数同步进名单（blocked_count ≥ 5）",
                count("SELECT COALESCE(MAX(blocked_count), 0) FROM t_security_list WHERE list_value = ? AND list_type = 1", honeypotIp) >= 5, "");
    }

    // ================================================================== 七、会员灰名单

    static void memberGrey(long member, String token, Map<String, int[]> t) throws Exception {
        resetMemberCounters(token, "REDEEM");
        String key = "rl:p:REDEEM:2:m:" + member;
        int levels = 0;
        for (int level = 1; level <= 4; level++) {
            for (int i = 0; i < 40; i++) {
                Resp r = send("POST", "/entitlement/999999999/claim", ip(), "{}", auth(token));
                if (r.status == 429 && r.retryAfter != null && r.retryAfter > 30) {
                    levels++;
                    break;
                }
            }
            redis.cmd("HSET", key, "until", String.valueOf(now() - 1));
        }
        boolean greyed = waitFor(Duration.ofSeconds(100), () ->
                count("SELECT COUNT(*) FROM t_security_list WHERE dimension = 4 AND list_type = 2 AND list_value = ? AND status = 1",
                        String.valueOf(member)) == 1);
        check("登录后场景（兑换）账号维度罚到第 4 档 → 会员自动进灰名单", levels == 4 && greyed, "罚到 " + levels + " 档");

        // 等网关拿到带它的快照（发布即时，网关 15 秒一次），然后账号维度阈值减半
        boolean reloaded = waitFor(Duration.ofSeconds(40), () -> {
            Object body = redis.cmd("HGET", "gw:rl:snapshot", "body");
            return body != null && body.toString().contains("\"memberId\":" + member);
        });
        Thread.sleep(16_000);
        resetMemberCounters(token, "REDEEM");
        int burst = t.get("REDEEM:2")[0];
        int half = (int) Math.ceil(burst * 0.5);
        int firstLimited = -1;
        for (int i = 1; i <= burst + 2 && firstLimited < 0; i++) {
            if (send("POST", "/entitlement/999999999/claim", ip(), "{}", auth(token)).status == 429) {
                firstLimited = i;
            }
        }
        check("灰名单会员在网关的账号维度阈值减半（突发 " + burst + " → " + half + "）",
                reloaded && firstLimited >= half + 1 && firstLimited <= half + 2 && firstLimited < burst + 1,
                "第一次 429 出现在第 " + firstLimited + " 次");
    }

    // ================================================================== 八、熔断

    static void breaker() throws Exception {
        String last = null;
        for (int i = 0; i < 55; i++) {
            last = ip();
            send("GET", "/.env", last, null, Map.of());
        }
        Resp after = send("GET", "/activity", last, null, Map.of());
        check("第 2 道保险：10 分钟内超过 50 个不同 IP 命中蜜罐 → 熔断，之后的命中不罚",
                after.status == 200 && redis.cmd("GET", "sec:pen:ip:" + last) == null, "最后一个 IP 的下一个请求 " + after.status);
    }

    // ================================================================== 造数与清理

    /** 场景:维度 → {突发, 3m, 5m, 10m} */
    static Map<String, int[]> thresholds() throws Exception {
        Map<String, int[]> m = new LinkedHashMap<>();
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT target, dimension, burst, limit_3m, limit_5m, limit_10m FROM t_rate_limit_rule WHERE target_type = 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.put(rs.getString(1) + ":" + rs.getInt(2), new int[]{rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)});
            }
        }
        return m;
    }

    static long createMember() throws Exception {
        long id = 9_980_000_000L + R.nextLong(9_999_999L);
        update("""
                INSERT INTO t_member (member_id, member_name, nickname, gender, status, register_source, create_time, last_active_time)
                VALUES (?, ?, ?, 0, 1, 'E2E', NOW(), NOW())""", id, "sv" + id, "会员" + id);
        return id;
    }

    /** 与 MemberRedisTokenStore 同格式：app:auth:t:{sha256(令牌)} = 会员号|会话号|登录时刻|端|设备|IP|地区 */
    static String mintSession(long member) throws Exception {
        String token = "mb_e2e_" + UUID.randomUUID().toString().replace("-", "");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        redis.cmd("SET", "app:auth:t:" + digest, member + "|e2e|" + System.currentTimeMillis() + "|H5|||", "EX", "3600");
        redis.cmd("SADD", "app:auth:m:" + member, digest);
        return token;
    }

    static void resetMemberCounters(String token, String scene) throws Exception {
        long member = memberOf(token);
        redis.cmd("DEL", "rl:c:" + scene + ":2:m:" + member);
        redis.cmd("DEL", "rl:p:" + scene + ":2:m:" + member);
    }

    static long memberOf(String token) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        Object v = redis.cmd("GET", "app:auth:t:" + digest);
        return Long.parseLong(v.toString().split("\\|")[0]);
    }

    static void cleanUp(long member) {
        try {
            update("DELETE FROM t_security_list WHERE dimension = 4 AND list_value = ?", String.valueOf(member));
            update("DELETE FROM t_member WHERE member_id = ?", member);
            Object digests = redis.cmd("SMEMBERS", "app:auth:m:" + member);
            if (digests instanceof List<?> list) {
                for (Object d : list) {
                    redis.cmd("DEL", "app:auth:t:" + d);
                }
            }
            redis.cmd("DEL", "app:auth:m:" + member);
        } catch (Exception e) {
            System.out.println("清理失败（不影响结论）: " + e);
        }
    }

    // ================================================================== 工具

    record Resp(int status, String body, Long retryAfter) {
    }

    static Resp send(String method, String path, String ip, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(GATEWAY + path)).timeout(Duration.ofSeconds(10))
                .header("X-Forwarded-For", ip);
        headers.forEach(b::header);
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Retry-After").map(Long::parseLong).orElse(null));
    }

    static Map<String, String> auth(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    static String stripTrace(String body) {
        return body.replaceAll("\"traceId\"\\s*:\\s*(\"[^\"]*\"|null)", "\"traceId\":\"-\"");
    }

    static String ip() {
        return "198." + (18 + R.nextInt(2)) + "." + R.nextInt(256) + "." + (1 + R.nextInt(254));
    }

    static String email() {
        return "e2e-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    static long now() {
        return System.currentTimeMillis() / 1000;
    }

    static void check(String name, boolean ok, String detail) {
        RESULTS.add(new String[]{ok ? "✅" : "❌", name, detail});
        System.out.println("   " + (ok ? "✅ " : "❌ ") + name + (ok || detail.isEmpty() ? "" : " —— " + detail));
    }

    static boolean waitFor(Duration max, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + max.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(3000);
        }
        return condition.getAsBoolean();
    }

    static long count(String sql, Object... args) {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void update(String sql, Object... args) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** 最小的 RESP 客户端：只为这一个脚本，不拉 Redis 驱动进来 */
    static final class Redis {

        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;

        Redis(String host, int port, int database) throws IOException {
            socket = new Socket(host, port);
            out = socket.getOutputStream();
            in = new BufferedInputStream(socket.getInputStream());
            cmd("SELECT", String.valueOf(database));
        }

        synchronized Object cmd(String... args) {
            try {
                StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
                for (String a : args) {
                    byte[] bytes = a.getBytes(StandardCharsets.UTF_8);
                    sb.append('$').append(bytes.length).append("\r\n").append(a).append("\r\n");
                }
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                return read();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private Object read() throws IOException {
            int type = in.read();
            String line = line();
            return switch (type) {
                case '+', ':' -> line;
                case '-' -> throw new IllegalStateException("Redis: " + line);
                case '$' -> {
                    int n = Integer.parseInt(line);
                    if (n < 0) {
                        yield null;
                    }
                    byte[] buf = in.readNBytes(n + 2);
                    yield new String(buf, 0, n, StandardCharsets.UTF_8);
                }
                case '*' -> {
                    int n = Integer.parseInt(line);
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(read());
                    }
                    yield list;
                }
                default -> throw new IllegalStateException("看不懂的 Redis 回复: " + (char) type);
            };
        }

        private String line() throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != '\r') {
                sb.append((char) c);
            }
            in.read();
            return sb.toString();
        }
    }
}
