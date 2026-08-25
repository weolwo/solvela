package solvela.admin.module.system.job.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.admin.module.system.job.api.domain.SolvelaJobQueryForm;
import solvela.admin.module.system.job.api.domain.SolvelaJobVO;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务 dao
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Mapper
public interface SolvelaJobDao extends BaseMapper<SolvelaJobEntity> {

    /**
     * 定时任务-分页查询
     */
    List<SolvelaJobVO> query(Page<?> page, @Param("query") SolvelaJobQueryForm queryForm);

    /**
     * 假删除
     */
    void updateDeletedFlag(@Param("jobId") Integer jobId, @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 根据执行器名称查找（同一执行器可能挂多个任务，故返回列表）
     */
    List<SolvelaJobEntity> selectByHandlerName(@Param("handlerName") String handlerName);

    /**
     * 是否存在该任务编码（用于 {@code generateUniqueBizCode} 判重）
     */
    boolean existsJobCode(@Param("jobCode") String jobCode);

    // ==================== 抢占式调度 ====================

    /**
     * 🔴 取数据库时钟。<b>整个调度链路的所有时间判断都必须以它为准</b>（铁律 9/10）。
     *
     * <p>不这么做的后果不是理论风险：多节点各自用 JVM 时钟，时钟漂移会直接表现为
     * 任务重跑或漏跑，而现象与「配置写错了」完全无法区分。
     */
    LocalDateTime selectDbNow();

    /**
     * 扫描到期任务。
     *
     * <p>预读窗口把「马上就要到点」的一并捞出来，避免边界抖动导致一秒漂移。
     * 走 {@code idx_next_trigger(app_env, enabled_flag, deleted_flag, next_trigger_time)}。
     */
    List<SolvelaJobEntity> selectDueJobList(@Param("appEnv") String appEnv,
                                            @Param("dueTime") LocalDateTime dueTime,
                                            @Param("limit") int limit);

    /**
     * 🔴 抢占执行权：影响行数 = 1 才算抢到。
     *
     * <p>{@code enabled_flag = 1 AND deleted_flag = 0} 两个条件是<b>状态防御</b>，缺了会出现
     * 「幽灵执行」：运营在扫描到数据与执行 UPDATE 之间的几毫秒里点了停用，
     * 任务依然被抢占并投递 —— 而运营的认知是「我明明关了」，属于最难自证的一类故障。
     *
     * @param version 读到的版本号，变了说明别的节点已经抢走
     */
    int preemptJob(@Param("jobId") Integer jobId,
                   @Param("version") Long version,
                   @Param("nextTriggerTime") LocalDateTime nextTriggerTime,
                   @Param("prevTriggerTime") LocalDateTime prevTriggerTime);

    /**
     * ONE_TIME 任务执行后置终态：不再计算下次触发
     */
    int markTerminal(@Param("jobId") Integer jobId);

    /**
     * 更新连续失败计数：成功传 0（清零），失败传 1（累加）
     */
    int updateContinuousFail(@Param("jobId") Integer jobId, @Param("increment") int increment);

    /**
     * 标记执行器失联，供后台列表标红
     */
    int updateHandlerMissing(@Param("jobId") Integer jobId, @Param("missing") boolean missing);

    /**
     * 🔴 「超期未触发」检测：调度器自监控的唯一手段。
     *
     * <p>前两条告警（连续失败、执行超时）只能告诉你任务跑错了，
     * <b>只有这条能告诉你任务根本没在跑</b> —— 扫描线程死掉时，系统会安静如常。
     */
    List<SolvelaJobEntity> selectOverdueJobList(@Param("appEnv") String appEnv,
                                                @Param("overdueTime") LocalDateTime overdueTime);

    /**
     * 清理终态的一次性任务（软删），防止列表被历史活动任务淹没
     */
    int deleteTerminalOneTimeJob(@Param("beforeTime") LocalDateTime beforeTime);
}
