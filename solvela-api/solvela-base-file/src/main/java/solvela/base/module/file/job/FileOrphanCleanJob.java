package solvela.base.module.file.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.file.service.FileAssetService;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;

import java.time.LocalDateTime;

/**
 * 孤儿文件清理：删掉「上传了但从来没有业务引用」的临时文件。
 *
 * <h3>为什么必须有人清</h3>
 * 用户在表单里选了图、然后关掉页面没提交 —— 这张图就永远停在 {@code TEMP}，
 * 没人引用，也没有任何机制会回来收它。跑几年之后存储里一半是垃圾，
 * 而且<b>你分不出哪些是垃圾</b>，因为那时候已经没有依据了。
 *
 * <p>{@code FileAssetService.delete} 的注释里从 2026-08 就写着「孤儿清理任务还没落地」，
 * 并且为此把「删除」做成了不可恢复的硬删 —— 就是在等这个任务。
 *
 * <h3>🔴 上线顺序：先回填，再开这个开关</h3>
 * 这个任务的判据是 {@code t_file_relation} 里有没有行。而在 2026-09-25 之前，
 * <b>有三处业务上传了图却从来没登记过引用</b>（商城分类图标、任务规则说明富文本、
 * 奖品扩展图）。直接开这个任务，那些<b>正在用的图会被当垃圾删掉</b>，
 * 表现是 C 端图标集体变叉，且不可恢复。
 *
 * <p>所以：
 * <ol>
 *   <li>三处 {@code confirm} 已补（同一次提交）</li>
 *   <li>跑一次 {@code fileRelationBackfill} 把<b>存量</b>引用补进去</li>
 *   <li>本任务先用 {@code dryRun} 跑一次，人眼看数量对不对</li>
 *   <li>再把 {@code t_solvela_job} 里这行的 {@code enabled_flag} 打开</li>
 * </ol>
 * 种子数据里这一行<b>刻意是停用的</b>，就是为了挡住第 2 步没做就上线。
 *
 * <h3>⚠️ 保护期不是「过期时间」，是「别删活人的东西」</h3>
 * {@code retainDays} 默认 7 天。一张刚上传 10 分钟、用户还在填表单的图，
 * 此刻同样满足「TEMP 且无引用」—— 保护期就是用来隔开这种情况的。
 * 调小它没有任何收益（省的那点存储不值钱），只会把窗口缩到危险的宽度。
 *
 * @author alaric
 * @date 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "fileOrphanClean",
        title = "【基础】孤儿文件清理",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "retainDays", desc = "保护期天数：只清这个天数之前上传的，默认 7",
                        type = JobParam.Type.INT, defaultValue = "7"),
                @JobParam(key = "dryRun", desc = "试运行：只统计将要删除的文件数，不删数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class FileOrphanCleanJob implements SolvelaJob {

    /**
     * 单批条数。比 {@code MqMessageLogCleanJob} 的 1000 小得多，因为这里每一条都要
     * <b>删一次对象存储</b>（一次网络往返），不是一条 SQL 删一千行。
     */
    private static final int BATCH_SIZE = 200;

    /** 单次执行最多跑多少批。清不完下次接着清 —— 它是幂等的 */
    private static final int MAX_BATCH_ROUND = 25;

    /**
     * 🔴 保护期下限。低于这个值直接拒绝执行。
     *
     * <p>不是洁癖：参数是能在后台页面上随手改的，而「把 7 改成 0 然后点执行」
     * 会把<b>此刻所有人正在填的表单里的图</b>一次性删光，且不可恢复。
     * 一个清理任务不该有能力造成这种事，哪怕操作的人自己想。
     */
    private static final int MIN_RETAIN_DAYS = 1;

    private final FileAssetService fileAssetService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        int retainDays = ctx.intParam("retainDays", 7);
        if (retainDays < MIN_RETAIN_DAYS) {
            throw new IllegalArgumentException(
                    "保护期不能小于 " + MIN_RETAIN_DAYS + " 天（当前传入 " + retainDays
                            + "）——  它挡的是「用户正在填的表单里那些还没提交的图」");
        }
        // 用库时钟，不用 JVM 的：两个时钟差几小时的话，删掉的就是不该删的那一批，而且不可逆
        LocalDateTime deadline = ctx.dbNow().minusDays(retainDays);

        if (ctx.boolParam("dryRun", false)) {
            long cleanable = fileAssetService.countOrphans(deadline);
            log.info("【孤儿文件清理】试运行：{} 之前上传且无人引用的临时文件有 {} 个", deadline, cleanable);
            return "试运行：有 " + cleanable + " 个孤儿文件可清理，本次未删除任何数据";
        }

        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            // 超时靠中断实现，每批开头自查一次，否则超时配了也砍不掉
            ctx.checkCancelled();
            int deleted = fileAssetService.purgeOrphans(deadline, BATCH_SIZE);
            total += deleted;
            if (deleted > 0) {
                // 卡住时这一行是唯一的线索：返回值只有跑完才有
                log.info("【孤儿文件清理】第 {} 批删除 {} 个，累计 {} 个", round + 1, deleted, total);
            }
            // ⚠️ 判据是「这一轮删掉的数量」，而它可能小于扫到的数量
            //（扫到之后被人引用了、或者删存储失败留到下一轮）。
            //   那些跳过的下一批还会被扫到同样的 id，于是这里会原地打转到 MAX_BATCH_ROUND。
            //   可以接受：它有上限、幂等，而且真出现大量跳过时日志里看得见。
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (total == 0) {
            return "没有需要清理的孤儿文件";
        }
        return "已清理 " + total + " 个孤儿文件（保护期 " + retainDays + " 天）";
    }
}
