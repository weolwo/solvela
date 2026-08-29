package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 枚举化改造的<b>棘轮</b>：盯住散落在 service 里的「状态魔法常量」，只许变少，不许变多。
 *
 * <h3>为什么需要它</h3>
 * 改造是一列一列推进的，于是长期存在「实体已经是枚举、别处还留着 Integer 常量」的中间态。
 * 这个中间态里藏着一个<b>编译器抓不到</b>的坑：
 *
 * <pre>{@code
 * private static final Integer PRIZE_STATUS_ENABLED = 1;
 * ...
 * if (!PRIZE_STATUS_ENABLED.equals(prize.getStatus())) { ... }   // getStatus() 已经返回枚举
 * }</pre>
 *
 * {@code Integer.equals(Object)} 接受任何类型，编译通过、测试不报，但它<b>恒为 false</b>。
 * 上面那两行真实存在过（PrizeItemStockService / LotteryPrizeAnalysisService）：
 * {@code t_prize_config.status} 改成枚举之后，两个页面把每一个奖品都标成了「已停用」。
 *
 * <h3>怎么用</h3>
 * 下面这份名单是<b>还没枚举化的列</b>残留的常量。每完成一列改造，就把对应的行删掉；
 * 名单为空之日，就是本测试可以删掉之时。
 *
 * <p>两个方向都会红：
 * <ul>
 *   <li>冒出名单外的新常量 —— 有人在往回走；</li>
 *   <li>名单里的常量已经不存在 —— 改造做完了但没销账，名单不缩短棘轮就失去意义。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-08-29
 */
class EnumMigrationRatchetTest {

    /** 抓所有 {@code private static final int/Integer NAME = 数字;}，再按名字过滤 */
    private static final Pattern MAGIC_STATUS_CONST = Pattern.compile(
            "private static final (?:int|Integer) ([A-Z][A-Z0-9_]*)\\s*=\\s*-?\\d+;");

    /** 名字里带这些词才算「状态语义」 */
    private static final List<String> STATUS_WORDS =
            List.of("STATUS", "MODE", "RESULT", "FLOW_TYPE", "USER_TYPE");

    /** 带这些词的一律不算：长度/精度这类算术常量，只是碰巧撞词 */
    private static final List<String> NOT_STATUS_WORDS = List.of("LENGTH", "SCALE", "SIZE");

    /**
     * 尚未枚举化的列留下的常量。<b>只许删，不许加。</b>
     */
    private static final Set<String> ALLOWED = Set.of(

            // ---- t_proposal_record.status ----
            "solvela-ledger/ledger/engine/AssetDispatchEngine.java#STATUS_EXECUTING",
            "solvela-ledger/ledger/engine/AssetDispatchEngine.java#STATUS_FAILED",
            "solvela-ledger/ledger/engine/AssetDispatchEngine.java#STATUS_PENDING_EXECUTE",
            "solvela-ledger/ledger/engine/AssetDispatchEngine.java#STATUS_SUCCESS",
            "solvela-risk/risk/proposal/service/ProposalRecordService.java#STATUS_FIRST_REVIEW",
            "solvela-risk/risk/proposal/service/ProposalRecordService.java#STATUS_PENDING_EXECUTE",
            "solvela-risk/risk/proposal/service/ProposalRecordService.java#STATUS_REJECTED",
            "solvela-risk/risk/proposal/service/ProposalRecordService.java#STATUS_SECOND_REVIEW",


            // ---- t_lottery_config.status ----
            "solvela-marketing/lottery/config/service/LotteryConfigBoardService.java#STATUS_ONLINE",
            "solvela-marketing/lottery/config/service/LotteryConfigService.java#STATUS_OFFLINE",
            "solvela-marketing/lottery/config/service/LotteryConfigService.java#STATUS_ONLINE",
            "solvela-marketing/lottery/prizerule/service/LotteryPrizeAnalysisService.java#LOTTERY_STATUS_ONLINE",
            "solvela-marketing/lottery/runtime/TicketIssueService.java#CONFIG_STATUS_ONLINE",

            // ---- t_task_record_flow.flow_type ----
            "solvela-marketing/stat/service/MarketingStatService.java#FLOW_TYPE_ADVANCE",
            "solvela-marketing/stat/service/MarketingStatService.java#FLOW_TYPE_DISCARD",

            // ---- t_task_record.status ----
            "solvela-marketing/task/record/service/TaskRecordService.java#STATUS_EXPIRED",

            // ---- t_task_config.status ----
            "solvela-marketing/task/taskconfig/service/TaskConfigService.java#STATUS_ACTIVE",
            "solvela-marketing/task/taskconfig/service/TaskConfigService.java#STATUS_OFFLINE",
            "solvela-marketing/task/taskconfig/service/TaskConfigService.java#STATUS_PENDING",

            // ---- t_task_event.status ----
            "solvela-marketing/task/taskevent/service/TaskEventDefService.java#STATUS_DISABLED",
            "solvela-marketing/task/taskevent/service/TaskEventDefService.java#STATUS_ENABLED",

            // ---- t_task_template.status ----
            "solvela-marketing/task/tasktemplate/service/TaskTemplateService.java#STATUS_DISABLED",
            "solvela-marketing/task/tasktemplate/service/TaskTemplateService.java#STATUS_ENABLED",

            // ---- t_promotion_config.status ----
            "solvela-risk/risk/promotionconfig/service/PromotionConfigService.java#STATUS_ENABLED");

    @Test
    @DisplayName("状态魔法常量只许变少：新增即视为改造在往回走")
    void 魔法常量清单没有增长() throws IOException {
        Set<String> found = scan();

        Set<String> added = new TreeSet<>(found);
        added.removeAll(ALLOWED);
        assertEquals(Set.of(), added,
                "出现了名单之外的状态魔法常量。要么改用枚举，要么说明理由后加进 ALLOWED —— "
                        + "特别注意：如果对应的列已经枚举化，`常量.equals(实体.getXxx())` 会恒为 false 且编译不报错");

        Set<String> stale = new TreeSet<>(ALLOWED);
        stale.removeAll(found);
        assertEquals(Set.of(), stale,
                "这些常量已经不存在了，请从 ALLOWED 里删掉对应的行 —— 名单不缩短，棘轮就失去意义");
    }

    private Set<String> scan() throws IOException {
        // surefire 的工作目录是模块目录（solvela-api/solvela-admin），上一级才是所有模块的根
        Path root = Paths.get("..").toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> sources = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/") && !s.contains("/target/");
                    })
                    .collect(Collectors.toList());

            Set<String> found = new TreeSet<>();
            for (Path p : sources) {
                Matcher m = MAGIC_STATUS_CONST.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) {
                    String name = m.group(1);
                    boolean statusLike = STATUS_WORDS.stream().anyMatch(name::contains)
                            && NOT_STATUS_WORDS.stream().noneMatch(name::contains);
                    if (statusLike) {
                        found.add(shorten(root, p) + "#" + name);
                    }
                }
            }
            return found;
        }
    }

    /** solvela-api/solvela-marketing/src/main/java/solvela/draw/X.java 缩成 solvela-marketing/draw/X.java */
    private String shorten(Path root, Path file) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        int i = rel.indexOf("/src/main/java/");
        if (i < 0) {
            return rel;
        }
        String module = rel.substring(0, i);
        String pkg = rel.substring(i + "/src/main/java/".length());
        return module + "/" + pkg.replaceFirst("^solvela/", "");
    }
}
