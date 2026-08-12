package net.lab1024.sa.base.module.support.job.bootstrap;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.module.support.job.config.SmartJobConfig;
import net.lab1024.sa.base.module.support.job.constant.SmartJobPresetEnum;
import net.lab1024.sa.base.module.support.job.constant.SmartJobTriggerTypeEnum;
import net.lab1024.sa.base.module.support.job.repository.SmartJobDao;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity;
import org.springframework.beans.factory.SmartInitializingSingleton;

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
public class SmartJobBootstrapInitializer implements SmartInitializingSingleton {

    private final SmartJobDao jobDao;

    private final SmartJobConfig jobConfig;

    public SmartJobBootstrapInitializer(SmartJobDao jobDao, SmartJobConfig jobConfig) {
        this.jobDao = jobDao;
        this.jobConfig = jobConfig;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            this.ensure("_jobLogClean", "【系统】清理定时任务执行日志",
                    "0 0 3 * * *", SmartJobPresetEnum.NORMAL, "自动创建：每天 03:00 清理过期执行日志");
            this.ensure("_jobZombieScan", "【系统】回收僵尸执行记录",
                    "0 */5 * * * *", SmartJobPresetEnum.LIGHT, "自动创建：每 5 分钟回收「执行中」僵尸记录");
            this.ensure("_jobHealthCheck", "【系统】调度健康自检与告警",
                    "0 */5 * * * *", SmartJobPresetEnum.LIGHT, "自动创建：每 5 分钟检查调度器是否仍在正常工作");
        } catch (Exception e) {
            // 自举失败不能拖垮启动 —— 表还没建、库还没连上都可能走到这里
            log.error("==== SmartJob ==== 内置任务自举失败（不影响启动，但系统任务不会运行）", e);
        }
    }

    private void ensure(String handlerName, String jobName, String cron,
                        SmartJobPresetEnum preset, String remark) {
        List<SmartJobEntity> exist = jobDao.selectByHandlerName(handlerName);
        if (!exist.isEmpty()) {
            return;
        }
        SmartJobEntity entity = new SmartJobEntity();
        entity.setJobCode(SmartCodeUtil.generateUniqueBizCode(jobDao::existsJobCode));
        entity.setJobName(jobName);
        entity.setHandlerName(handlerName);
        entity.setJobGroup("SYSTEM");
        entity.setTriggerType(SmartJobTriggerTypeEnum.CRON.getValue());
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

        jobDao.insert(entity);
        log.info("==== SmartJob ==== 已自动创建内置任务：{}（{}）", jobName, cron);
    }
}
