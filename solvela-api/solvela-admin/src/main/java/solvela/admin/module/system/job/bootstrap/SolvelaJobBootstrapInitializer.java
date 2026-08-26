package solvela.admin.module.system.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.admin.module.system.job.constant.SolvelaJobPresetEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerTypeEnum;
import solvela.admin.module.system.job.repository.SolvelaJobDao;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

/**
 * 内置任务自举：启动时把三个系统任务写进库（已存在则不动）。
 *
 * <p>为什么要自举而不是写进 DDL：这三个任务是<b>调度器自身的一部分</b>，
 * 跟着代码走而不是跟着数据走。写进 DDL 的话，新环境建库忘了执行某个版本的脚本，
 * 就会得到一个「不会自我清理、不会自我监控」的调度器 —— 而且没有任何迹象。
 *
 * <p>⚠️ 只新增、<b>不覆盖</b>：运营调过的 cron、停用状态都要保留。
 * 这与「向导生成的任务被人工改过就不再覆盖」是同一条原则 ——
 * 默认静默覆盖是最容易让人半夜白干的行为。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SolvelaJobBootstrapInitializer implements SmartInitializingSingleton {

    private final SolvelaJobDao jobDao;

    private final SolvelaJobConfig jobConfig;

    public SolvelaJobBootstrapInitializer(SolvelaJobDao jobDao, SolvelaJobConfig jobConfig) {
        this.jobDao = jobDao;
        this.jobConfig = jobConfig;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            this.ensure("_jobLogClean", "【系统】清理定时任务执行日志",
                    "0 0 3 * * *", SolvelaJobPresetEnum.NORMAL, "自动创建：每天 03:00 清理过期执行日志");
            this.ensure("_jobZombieScan", "【系统】回收僵尸执行记录",
                    "0 */5 * * * *", SolvelaJobPresetEnum.LIGHT, "自动创建：每 5 分钟回收「执行中」僵尸记录");
            this.ensure("_jobHealthCheck", "【系统】调度健康自检与告警",
                    "0 */5 * * * *", SolvelaJobPresetEnum.LIGHT, "自动创建：每 5 分钟检查调度器是否仍在正常工作");
        } catch (Exception e) {
            // 自举失败不能拖垮启动 —— 表还没建、库还没连上都可能走到这里
            log.error("==== SolvelaJob ==== 内置任务自举失败（不影响启动，但系统任务不会运行）", e);
        }
    }

    private void ensure(String handlerName, String jobName, String cron,
                        SolvelaJobPresetEnum preset, String remark) {
        List<SolvelaJobEntity> exist = jobDao.selectByHandlerName(handlerName);
        if (!exist.isEmpty()) {
            return;
        }
        SolvelaJobEntity entity = new SolvelaJobEntity();
        // 🔴 系统任务用**确定性编码**，而不是随机生成。
        //    上面那句 selectByHandlerName 是典型的 check-then-act：两个 WORKER 节点同时启动，
        //    都查到「不存在」、都往下走插入 —— 而 handler_name 上<b>没有</b>唯一约束
        //    （那是刻意的：同一个执行器允许挂 N 个任务）。结果是三个系统任务各被插两份，
        //    然后同一份工作被两条配置各跑一遍。单节点下这个竞态永远不会显形。
        //    编码确定之后，uk_job_code 就成了天然的幂等键：谁先插谁赢，输的那个吃 DuplicateKey。
        entity.setJobCode(systemJobCode(handlerName));
        entity.setJobName(jobName);
        entity.setHandlerName(handlerName);
        entity.setJobGroup("SYSTEM");
        entity.setTriggerType(SolvelaJobTriggerTypeEnum.CRON.getValue());
        entity.setTriggerValue(cron);
        // 🔴 立刻可被扫到，但下一次触发按 cron 排 —— 不预置的话它要等到第一次 cron 命中才动
        entity.setNextTriggerTime(java.time.LocalDateTime.now());
        entity.setTriggerVersion(0L);

        entity.setPresetCode(preset.getValue());
        entity.setJitterSeconds(preset.getJitterSeconds());
        entity.setTimeoutSeconds(preset.getTimeoutSeconds());
        entity.setRetryTimes(preset.getRetryTimes());
        entity.setRetryInterval(30);
        entity.setMisfireStrategy(preset.getMisfireStrategy().getValue());
        entity.setMisfireThresholdSec(preset.getMisfireThresholdSeconds());
        entity.setBlockStrategy(preset.getBlockStrategy().getValue());

        entity.setAppEnv(jobConfig.getEnv());
        entity.setEnabledFlag(true);
        entity.setDeletedFlag(false);
        entity.setTerminalFlag(false);
        entity.setHandlerMissingFlag(false);
        entity.setContinuousFailCount(0);
        entity.setManualModifiedFlag(false);
        entity.setSource("SYSTEM");
        entity.setOwnerBizType("SYSTEM");
        entity.setSort(0);
        entity.setRemark(remark);
        entity.setUpdateName("system");

        try {
            jobDao.insert(entity);
            log.info("==== SolvelaJob ==== 已自动创建内置任务：{}（{}）", jobName, cron);
        } catch (DuplicateKeyException e) {
            // 别的节点抢先插入了 —— 这不是错误，是幂等生效
            log.info("==== SolvelaJob ==== 内置任务已由其它节点创建，跳过：{}", jobName);
        }
    }

    /**
     * 由 handler 名派生出稳定的 10 位业务编码（铁律 8：{@code ^[A-Z0-9]{10}$}）。
     *
     * <p>同一个 handler 名在任何节点、任何时刻算出的编码都相同 ——
     * 这正是它能当幂等键用的原因。
     *
     * <p>用 36 进制而不是 hex：36 进制的字符集恰好是 {@code 0-9a-z}，
     * 转大写后天然落在 {@code [A-Z0-9]} 内，不需要额外过滤。
     */
    static String systemJobCode(String handlerName) {
        // 取绝对值前先处理 Integer.MIN_VALUE（它取绝对值仍是负数）
        long hash = Math.abs((long) handlerName.hashCode());
        String base36 = Long.toString(hash, 36).toUpperCase();
        // 不足补 0、超长截断，保证恰好 10 位
        return base36.length() >= 10
                ? base36.substring(0, 10)
                : "0".repeat(10 - base36.length()) + base36;
    }
}
