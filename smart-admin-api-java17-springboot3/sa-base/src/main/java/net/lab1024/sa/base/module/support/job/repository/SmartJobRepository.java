package net.lab1024.sa.base.module.support.job.repository;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * job 持久化业务。
 *
 * <p>🔴 <b>这个类存在的意义是「事务方法必须在独立 Bean 里」</b>（铁律 11）：
 * {@code @Transactional} 靠 Spring AOP 代理生效，同类内部方法互调不经过代理、
 * 注解<b>静默失效</b> —— 没事务、不报错、编译和单测全过，只在数据不一致时才暴露。
 * 调度链路里的写操作一律经由本类。
 *
 * @author huke
 * @date 2024/6/22 22:28
 */
@Slf4j
@Service
public class SmartJobRepository {

    @Autowired
    private SmartJobDao jobDao;

    @Autowired
    private SmartJobLogDao jobLogDao;

    public SmartJobDao getJobDao() {
        return jobDao;
    }

    public SmartJobLogDao getJobLogDao() {
        return jobLogDao;
    }

    /**
     * 插入执行记录并回写「最后一次执行」。
     *
     * <p>唯一索引 {@code uk_job_trigger} 冲突时会抛 {@code DuplicateKeyException}，
     * 由调用方捕获 —— <b>那不是错误，是防重生效了</b>：
     * 说明别的节点已经跑过这个触发点，本次直接放弃即可。
     */
    @Transactional(rollbackFor = Throwable.class)
    public void saveLog(SmartJobLogEntity logEntity) {
        jobLogDao.insert(logEntity);
        SmartJobEntity updateJob = new SmartJobEntity();
        updateJob.setJobId(logEntity.getJobId());
        updateJob.setLastExecuteTime(logEntity.getExecuteStartTime());
        updateJob.setLastExecuteLogId(logEntity.getLogId());
        jobDao.updateById(updateJob);
    }

    /**
     * 只插一条记录（BLOCKED / MISFIRE 这类「没真跑」的记录用）。
     *
     * <p>它们不该回写 {@code last_execute_time} —— 那一列的语义是「最后一次<b>执行</b>」，
     * 把「被丢弃」也算进去会让运营误以为任务跑过了
     */
    public void saveLogOnly(SmartJobLogEntity logEntity) {
        jobLogDao.insert(logEntity);
    }

    /**
     * 🔴 落终态 + 安排重试，<b>必须在同一个事务里</b>。
     *
     * <p>不同事务的后果：FAIL 写成功、重试记录插失败 → <b>重试静默丢失</b>，
     * 而日志上看起来一切正常（有一条失败记录，符合预期），
     * 排查时根本不会想到「本该有一条重试记录」。
     *
     * @param retryEntity 为 null 表示不需要重试
     * @param failIncrement 1 = 累加连续失败计数，0 = 成功后清零
     */
    @Transactional(rollbackFor = Throwable.class)
    public void finishAndScheduleRetry(SmartJobLogEntity finishedLog,
                                       SmartJobLogEntity retryEntity,
                                       Integer jobId,
                                       int failIncrement,
                                       Long finishedLogId) {
        jobLogDao.updateById(finishedLog);
        if (null != retryEntity) {
            jobLogDao.insert(retryEntity);
        }
        jobDao.updateContinuousFail(jobId, failIncrement);
        if (null != finishedLogId) {
            SmartJobEntity updateJob = new SmartJobEntity();
            updateJob.setJobId(jobId);
            updateJob.setLastExecuteLogId(finishedLogId);
            jobDao.updateById(updateJob);
        }
    }
}
