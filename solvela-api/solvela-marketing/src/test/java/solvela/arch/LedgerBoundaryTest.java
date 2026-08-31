package solvela.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 营销侧不许直接碰资产域（ledger）与提案域的行为（risk.proposal 的 service/dao/manager/api/job）。
 *
 * <h3>这是本仓最该守的一条缝</h3>
 * 目标形态是：<b>资产独立成服务</b>，会员 + 资产用一个独立的后台控制台。
 * 也就是说 marketing ↔ ledger 就是将来那条服务边界。
 *
 * <p>2026-08-31 撤掉 app-member 之后，两边跑在同一个进程里（solvela-app-biz），
 * {@code BizApplication} 也从「只扫 risk 的两个子包」改成了全包扫描 ——
 * 于是这条缝没有任何运行时边界拦着了。本测试就是那条缝剩下的全部保障：
 * 它红了别去改它，去改代码。
 *
 * <p>把边界留住，「将来把资产拆出去」才是<b>加一个进程壳 + 换一个 bean</b>；
 * 守不住的话，六个月后这两个域会长死在一起，那时再拆就是重写。
 *
 * <h3>合法的路只有一条：MemberProposalApi</h3>
 * 发奖侧要把奖交给资产侧，走 {@code MemberProposalApi.createProposal}，当场拿到
 * 「受理 / 拒绝 + 原因」。今天它在本进程里解析成 {@code ProposalApiService}，
 * 拆出去之后解析成 HTTP 代理 —— 调用方一行不改。
 *
 * <h3>为什么放行 risk.proposal.domain</h3>
 * 四个发奖 handler 现在先拼 {@code ProposalRecordAddCommand}、再由
 * {@code ProposalCmdMapper} 翻成 {@code CreateProposalCmd}。那是一个<b>已知的过渡形态</b>，
 * {@code ProposalCmdMapper} 的类注释自己写着：「等 consumer 整体搬到营销服务之后，
 * 四个 handler 应当直接拼 CreateProposalCmd，本类随之删除」。
 *
 * <p>consumer 已于 2026-08-31 并入 solvela-marketing，<b>那个前提已经成立</b>。
 * 等四个 handler 改完、ProposalCmdMapper 删掉之后，把 {@link #ALLOWED_PROPOSAL_SUBPACKAGE}
 * 这条放行去掉，本测试就能禁掉整个 {@code risk.proposal}。
 */
class LedgerBoundaryTest {

    /** 资产域。营销侧一个字都不该认识它 —— pom 里也刻意没有 solvela-ledger */
    private static final String LEDGER = "solvela/ledger/";

    /** 提案域。放行 domain（纯数据类型），禁掉 service / dao / manager / api / job */
    private static final Pattern PROPOSAL = Pattern.compile("solvela/risk/proposal/([a-z]+)/");

    /** 见类注释：过渡形态，handler 改完之后删掉这条放行 */
    private static final String ALLOWED_PROPOSAL_SUBPACKAGE = "domain";

    @Test
    @DisplayName("🔴 pom 里不许出现 solvela-ledger")
    void 本模块不许依赖资产域() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("<artifactId>solvela-marketing</artifactId>"),
                "读到的不是 solvela-marketing 的 pom，测试的工作目录变了");
        assertEquals(-1, pom.indexOf("<artifactId>solvela-ledger</artifactId>"),
                """
                        solvela-marketing 的 pom 里出现了 solvela-ledger。

                        资产域是将来要独立成服务的那一半（会员 + 资产一个独立控制台），
                        marketing → ledger 是那条服务边界，加上这条依赖就等于把它抹掉了。

                        要动资产走 MemberProposalApi.createProposal —— 那个契约今天解析成
                        进程内的 ProposalApiService，拆出去之后解析成 HTTP 代理，调用方不用改。
                        """);
    }

    @Test
    @DisplayName("🔴 本模块的 class 不许引用 ledger / risk.proposal 的行为")
    void 不许绕过契约直接调资产与提案() throws IOException {
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes),
                "没找到 target/classes —— 本测试要在 compile 之后跑，否则它会假装通过");

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String bytes = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                String name = classes.relativize(f).toString();

                if (bytes.contains(LEDGER)) {
                    offenders.add(name + "  ->  " + LEDGER.replace('/', '.'));
                }
                Matcher m = PROPOSAL.matcher(bytes);
                while (m.find()) {
                    if (!ALLOWED_PROPOSAL_SUBPACKAGE.equals(m.group(1))) {
                        offenders.add(name + "  ->  solvela.risk.proposal." + m.group(1));
                    }
                }
            }
        }

        // 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟，因为它给人已经守住了的错觉
        assertTrue(scanned > 100, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");

        assertTrue(offenders.isEmpty(),
                () -> """
                        营销侧直接引用了资产域 / 提案域的行为：
                          %s

                        这条缝是「将来能不能便宜地把资产拆成独立服务」的全部保障。
                        合法的路只有一条：MemberProposalApi.createProposal(CreateProposalCmd)，
                        它当场返回「受理 / 拒绝 + 原因」，今天是进程内调用、拆出去之后是 HTTP，
                        调用方代码一行不改。

                        绕过它直接调 ProposalRecordService 或 ledger 的 service，
                        在今天完全能跑（两边同进程、同一个库），坏的是拆的那一天 ——
                        那时这些调用点要一个个找出来重写，而它们不会有任何标记。
                        """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }
}
