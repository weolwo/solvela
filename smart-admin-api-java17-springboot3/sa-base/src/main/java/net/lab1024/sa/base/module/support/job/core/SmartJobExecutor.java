package net.lab1024.sa.base.module.support.job.core;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.util.SmartIpUtil;
import net.lab1024.sa.base.module.support.job.constant.SmartJobConst;
import net.lab1024.sa.base.module.support.job.constant.SmartJobUtil;
import net.lab1024.sa.base.module.support.job.repository.SmartJobRepository;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobLogEntity;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.StopWatch;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务 执行器
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Slf4j
public class SmartJobExecutor implements Runnable {

    private final SmartJobEntity jobEntity;

    private final SmartJobRepository jobRepository;

    private final SmartJob jobInterface;

    private final RedissonClient redissonClient;

    private static final String EXECUTE_LOCK = "smart-job-lock-execute-";

    /**
     * 执行结果列（t_smart_job_log.execute_result）的长度上限，堆栈超出必须先截断再落库。
     * 见铁律：往列里塞超长文本会在异常分支上二次抛异常，把原本要记录的失败原因彻底吞掉。
     */
    private static final int EXECUTE_RESULT_MAX_LENGTH = 1800;

    public SmartJobExecutor(SmartJobEntity jobEntity,
                            SmartJobRepository jobRepository,
                            SmartJob jobInterface,
                            RedissonClient redissonClient) {
        this.jobEntity = jobEntity;
        this.jobRepository = jobRepository;
        this.jobInterface = jobInterface;
        this.redissonClient = redissonClient;
    }

    /**
     * 系统线程执行
     */
    @Override
    public void run() {
        // 获取当前任务执行锁 最多持有30s自动释放
        Integer jobId = jobEntity.getJobId();
        RLock rLock = redissonClient.getLock(EXECUTE_LOCK + jobId);
        try {
            boolean lock = rLock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!lock) {
                return;
            }
            // 查询上次执行时间 校验执行间隔
            SmartJobEntity dbJobEntity = jobRepository.getJobDao().selectById(jobId);
            if (null == dbJobEntity) {
                return;
            }
            LocalDateTime lastExecuteTime = dbJobEntity.getLastExecuteTime();
            if (null != lastExecuteTime) {
                LocalDateTime nextTime = SmartJobUtil.queryNextTimeFromLast(jobEntity.getTriggerType(), jobEntity.getTriggerValue(), lastExecuteTime, 1).get(0);
                if (LocalDateTime.now().isBefore(nextTime)) {
                    return;
                }
            }
            // 执行任务
            SmartJobLogEntity logEntity = this.execute(SmartJobConst.SYSTEM_NAME);
            log.info("==== SmartJob ==== execute job->{},time-millis->{}ms", jobEntity.getJobName(), logEntity.getExecuteTimeMillis());
        } catch (Throwable t) {
            log.error("==== SmartJob ==== execute err:", t);
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    /**
     * 执行任务
     *
     * @param executorName
     */
    public SmartJobLogEntity execute(String executorName) {
        // 保存执行记录
        LocalDateTime startTime = LocalDateTime.now();
        Long logId = this.saveLogBeforeExecute(jobEntity, executorName, startTime);

        // 执行计时
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 执行任务
        boolean successFlag = true;
        String executeResult;
        try {
            executeResult = jobInterface.run(jobEntity.getParam());
            stopWatch.stop();
        } catch (Throwable t) {
            stopWatch.stop();
            successFlag = false;
            // ps:异常信息不大于数据库字段长度限制
            executeResult = stackTraceToString(t, EXECUTE_RESULT_MAX_LENGTH);
            log.error("==== SmartJob ==== execute err:", t);
        }

        // 更新执行记录
        SmartJobLogEntity logEntity = new SmartJobLogEntity();
        logEntity.setLogId(logId);
        logEntity.setSuccessFlag(successFlag);
        long totalTimeMillis = stopWatch.getTotalTimeMillis();
        logEntity.setExecuteTimeMillis(totalTimeMillis);
        logEntity.setExecuteEndTime(startTime.plus(totalTimeMillis, ChronoUnit.MILLIS));
        logEntity.setExecuteResult(executeResult);
        jobRepository.getJobLogDao().updateById(logEntity);
        return logEntity;
    }

    /**
     * 执行前 保存执行记录
     *
     * @param jobEntity
     * @param executorName
     * @param executeTime
     * @return 返回执行记录id
     */
    private Long saveLogBeforeExecute(SmartJobEntity jobEntity,
                                      String executorName,
                                      LocalDateTime executeTime) {
        Integer jobId = jobEntity.getJobId();
        // 保存执行记录
        SmartJobLogEntity logEntity = new SmartJobLogEntity();
        logEntity.setJobId(jobId);
        logEntity.setJobName(jobEntity.getJobName());
        logEntity.setParam(jobEntity.getParam());
        logEntity.setSuccessFlag(true);
        // 执行开始时间
        logEntity.setExecuteStartTime(executeTime);
        logEntity.setExecuteEndTime(executeTime);
        logEntity.setExecuteTimeMillis(0L);
        logEntity.setCreateName(executorName);
        logEntity.setIp(SmartIpUtil.getLocalFirstIp());
        logEntity.setProcessId(SmartJobUtil.getProcessId());
        logEntity.setProgramPath(SmartJobUtil.getProgramPath());

        // 更新最后执行时间
        SmartJobEntity updateJobEntity = new SmartJobEntity();
        updateJobEntity.setJobId(jobId);
        updateJobEntity.setLastExecuteTime(executeTime);
        jobRepository.saveLog(logEntity, updateJobEntity);
        return logEntity.getLogId();
    }

    /**
     * 查询 当前任务信息
     *
     * @return
     */
    public SmartJobEntity getJob() {
        return jobEntity;
    }

    /**
     * 异常堆栈转字符串，并按 maxLength 截断
     */
    private static String stackTraceToString(Throwable throwable, int maxLength) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(printWriter);
        }
        String stackTrace = stringWriter.toString();
        return stackTrace.length() > maxLength ? stackTrace.substring(0, maxLength) : stackTrace;
    }
}
