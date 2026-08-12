package net.lab1024.sa.base.module.support.job.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.base.module.support.job.api.domain.SmartJobLogQueryForm;
import net.lab1024.sa.base.module.support.job.api.domain.SmartJobLogVO;
import net.lab1024.sa.base.module.support.job.repository.domain.SmartJobLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务-执行记录 dao
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Mapper
public interface SmartJobLogDao extends BaseMapper<SmartJobLogEntity> {

    /**
     * 定时任务-执行记录-分页查询
     */
    List<SmartJobLogVO> query(Page<?> page, @Param("query") SmartJobLogQueryForm queryForm);

    /**
     * 🔴 扫描待执行记录：<b>手动触发与失败重试共用同一个状态机</b>。
     *
     * <p>两者都表现为一条 {@code status = PENDING} 的记录，区别只在 {@code fire_time} ——
     * 手动是 now，重试是 now + interval。<b>所以扫描只有一个条件，没有分支。</b>
     *
     * <p>走 {@code idx_pending(app_env, status, fire_time)}。
     */
    List<SmartJobLogEntity> selectPendingList(@Param("appEnv") String appEnv,
                                              @Param("dbNow") LocalDateTime dbNow,
                                              @Param("limit") int limit);

    /**
     * 🔴 抢占日志行：抢的是 {@code t_smart_job_log.status}，<b>不是</b> {@code job.trigger_version}。
     *
     * <p>抢版本号会连带改写 {@code next_trigger_time}，<b>扰乱正常的 cron 节奏</b> ——
     * 运营点一次「立即执行」，下一次定时就漂了。两条链路抢两个不同的对象，互不干扰。
     *
     * @return 影响行数 = 1 表示抢到
     */
    int preemptPendingLog(@Param("logId") Long logId,
                          @Param("ip") String ip,
                          @Param("startTime") LocalDateTime startTime);

    /**
     * 某任务是否还有未结束的执行记录（阻塞策略的 DB 兜底判据）。
     *
     * <p>Redis 锁是快判，这里是兜底：节点崩溃时锁没了，但 RUNNING 记录还在。
     * 两层缺一不可 —— 只有 Redis 锁，节点崩了就永远判不出阻塞；
     * 只有 DB，每次判断都要查库，扫描线程扛不住。
     *
     * <p>🔴 <b>必须排除本次自己的记录</b>（{@code excludeLogId}）。
     * 手动 / 重试路径是「先把日志行抢成 RUNNING、再判阻塞」，
     * 不排除自己就会<b>数到自己</b> —— 于是这条路径<b>永远被判定为阻塞、永远跑不起来</b>，
     * 而界面上显示的是「上一次执行尚未结束」，指向一个根本不存在的上一次。
     * 2026-08-12 实测复现，编译期完全看不出来。
     */
    int countRunning(@Param("jobId") Integer jobId,
                     @Param("earliestStartTime") LocalDateTime earliestStartTime,
                     @Param("excludeLogId") Long excludeLogId);

    /**
     * 僵尸记录：{@code RUNNING} 且开始时间早于阈值。
     *
     * <p>节点崩溃会留下永远不会结束的 RUNNING 记录，它会让阻塞判断<b>永久误判</b>为
     * 「上一次还在跑」，于是这个任务再也不会被执行。
     */
    List<SmartJobLogEntity> selectZombieList(@Param("beforeTime") LocalDateTime beforeTime,
                                             @Param("limit") int limit);

    /**
     * 清理 N 天前的执行日志
     */
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);
}
