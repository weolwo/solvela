package solvela.admin.module.system.job.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.file.service.FileAssetService;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.mall.category.service.MallCategoryService;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.task.taskconfig.service.TaskConfigService;

import java.time.LocalDateTime;

/**
 * 文件引用存量回填：<b>跑一次就够，然后就可以开孤儿清理任务了</b>。
 *
 * <h3>🔴 它解决的是一个「顺序不对就删数据」的问题</h3>
 * {@code FileOrphanCleanJob} 的判据是 {@code t_file_relation} 里有没有行。
 * 但 2026-09-25 之前有三处业务<b>上传了图却从来没登记过引用</b>：
 * <ul>
 *   <li>商城分类图标 —— {@code mall-category-form.vue} 的 ImageSlot 是真上传</li>
 *   <li>任务规则说明的富文本内嵌图 —— wangEditor 走 {@code CONTENT} 分类真上传</li>
 *   <li>奖品扩展图 —— {@code prize-config-form.vue} 的 ImageField 真上传，
 *       存在 {@code ext} 这个 JSON 列里</li>
 * </ul>
 * 同一次提交已经把三处的 {@code confirm} 补上了，但那<b>只管以后新存的</b>。
 * 存量那些图依旧是「TEMP 且无人引用」—— 清理任务眼里和垃圾没有区别。
 *
 * <p>所以正确顺序是：<b>先跑这个，再开清理</b>。反过来的后果是
 * C 端分类图标、任务规则配图、奖品图集体变成叉，而且<b>不可恢复</b>
 *（{@code FileAssetService} 删文件时连对象存储一起删）。
 *
 * <h3>⚠️ 为什么它在 admin 而不在某个业务模块</h3>
 * 它要同时够到 mall(20)、marketing(19)、prize(52) 三个模块。
 * 按模块顺序，只有 admin(22 之后的聚合层) 能同时看见它们 ——
 * 硬塞进其中任何一个都会造出一条反向依赖。
 *
 * <h3>⚠️ 跑完不会自动删掉自己</h3>
 * 它是幂等的（{@code confirm} 先清后建），重复跑无害。留着的价值是：
 * 哪天又发现一处漏登记的业务，补完 {@code confirm} 之后还得再回填一次。
 *
 * @author alaric
 * @date 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "fileRelationBackfill",
        title = "【基础】文件引用存量回填（开孤儿清理前跑一次）",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600
)
public class FileRelationBackfillJob implements SolvelaJob {

    private final MallCategoryService mallCategoryService;
    private final TaskConfigService taskConfigService;
    private final PrizeConfigService prizeConfigService;
    private final FileAssetService fileAssetService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        // 回填前后各数一次「孤儿」，差值就是这次救回来的文件数 ——
        // 只打印「处理了 N 条配置」看不出效果，而这个数字正是要给人看的那个
        LocalDateTime deadline = ctx.dbNow();
        long before = fileAssetService.countOrphans(deadline);

        int categories = mallCategoryService.backfillIconRelations();
        ctx.checkCancelled();
        int taskConfigs = taskConfigService.backfillRuleDescRelations();
        ctx.checkCancelled();
        int prizeConfigs = prizeConfigService.backfillExtImageRelations();

        long after = fileAssetService.countOrphans(deadline);
        long rescued = before - after;

        log.info("【文件引用回填】分类 {} 条、任务配置 {} 条、奖品配置 {} 条；"
                        + "孤儿数 {} -> {}，救回 {} 个文件",
                categories, taskConfigs, prizeConfigs, before, after, rescued);

        return "已回填：分类 " + categories + " 条、任务配置 " + taskConfigs
                + " 条、奖品配置 " + prizeConfigs + " 条。"
                + "无人引用的文件从 " + before + " 个降到 " + after + " 个"
                + (rescued > 0 ? "，救回 " + rescued + " 个原本会被清理掉的文件" : "");
    }
}
