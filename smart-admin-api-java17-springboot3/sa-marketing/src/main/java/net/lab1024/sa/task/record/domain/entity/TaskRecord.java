package net.lab1024.sa.task.record.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
@TableName("t_task_record")
public class TaskRecord {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 会员名
     */
    private String memberName;

    /**
     * 任务配置ID
     */
    private Long taskConfigId;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 业务期数标识(防重用)：NONE, 日期(20260402)
     */
    private String periodKey;

    /**
     * 开始时间
     */
    private LocalDateTime validStartTime;

    /**
     * 过期时间
     */
    private LocalDateTime validEndTime;

    /**
     * 当前进度值：如已签到 3.0000 天
     */
    private BigDecimal currentMetric;

    /**
     * 乐观锁版本号（v3.44.0 新增）。
     *
     * <p>⚠️ 这一列是给 STREAK 用的，不是给 COUNT/AMOUNT 用的。
     * 累加型走 {@code TaskRecordDao.advanceMetric} 的条件更新（一条 SQL、零冲突、零重试）；
     * 只有 STREAK 必须先读 lastHitDate 才知道是「清零再+1」还是「+1」，
     * 读-改-写无法避免，才用版本号 + 有限次重试。
     * 别因为有了这一列就把三个策略都改成读-改-写。
     *
     * <p>刻意<b>不加</b> {@code @Version} 注解：本项目的乐观锁一律把版本号压进自定义 SQL 的 WHERE
     * （对齐 t_prize_pool_item / t_member_wallet 的写法），
     * 加了注解会让 updateById 也隐式带上版本条件，与显式 SQL 两套语义并存。
     */
    private Integer version;

    /**
     * 状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期
     *
     * <p>⚠️ 阶梯任务下，「第 1 档已发奖、第 2 档进行中」<b>没有</b>对应取值 ——
     * 这是刻意的：status 只表示<b>最高档</b>是否达标，低档的发放情况记在
     * progress_data.dispatchedStages（展示用，非判据）。发奖防重的唯一判据是
     * t_prize_log.uk_external_biz。详见方案 §4.8。
     */
    private Integer status;

    /**
     * 进度详情
     */
    private String progressData;

    /**
     * 接取任务时的规则快照
     */
    private String ruleSnapshot;

    /**
     * 接取任务时的奖励快照
     */
    private String prizeSnapshot;

    /**
     * 达标时间
     */
    private LocalDateTime completeTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
