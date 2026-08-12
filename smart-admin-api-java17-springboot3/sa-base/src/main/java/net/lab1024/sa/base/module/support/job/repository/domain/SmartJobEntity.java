package net.lab1024.sa.base.module.support.job.repository.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import net.lab1024.sa.base.module.support.job.constant.SmartJobTriggerTypeEnum;

import java.time.LocalDateTime;

/**
 * 定时任务 实体类
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
@Data
@TableName("t_smart_job")
public class SmartJobEntity {

    @TableId(type = IdType.AUTO)
    private Integer jobId;

    /**
     * 任务编码：10 位大写字母+数字（铁律 8），全局唯一。
     *
     * <p>为什么需要它：{@code handler_name} 不再唯一（同一执行器可挂 N 个任务），
     * 而 {@code job_id} 是自增主键、跨环境不一致，不适合对外。
     * 日志、告警、跳转链接都指向这个编码。
     */
    private String jobCode;

    private String jobName;

    /**
     * 执行器名称，对应 {@code @SmartJobHandler#name()}。
     *
     * <p>🔴 原来这里存的是执行类的全限定名，实现类被 CGLIB 代理后就永远匹配不上了
     * （不报错、不打日志、任务静默不跑）。现在存注解声明的名字，与类名彻底解耦。
     */
    private String handlerName;

    /**
     * 分组：SYSTEM / DATA / ACTIVITY / OPS / BUSINESS
     */
    private String jobGroup;

    /**
     * @see SmartJobTriggerTypeEnum
     */
    private String triggerType;

    private String triggerValue;

    /**
     * 🔴 下次触发时间：<b>调度的唯一真源</b>，由数据库时钟产生。
     *
     * <p>它落库带来三件事：时钟只剩数据库一个（铁律 9/10）；
     * 停机期间漏掉的调度在库里看得见（值停在过去），可按策略补跑；
     * 配置变更靠版本号天然感知，pub/sub 降级成纯加速通道。
     */
    private LocalDateTime nextTriggerTime;

    private LocalDateTime prevTriggerTime;

    /**
     * 🔴 抢占乐观锁版本号。抢占语句：
     * {@code UPDATE ... WHERE job_id = ? AND trigger_version = ? AND enabled_flag = 1 AND deleted_flag = 0}，
     * 影响行数 = 1 才算抢到
     */
    private Long triggerVersion;

    /**
     * 打散秒数：按 jobId 确定性偏移，防整点惊群；ONE_TIME 强制 0
     */
    private Integer jitterSeconds;

    private String param;

    /**
     * 预设档位，仅记录来源。落库的是展开后的具体值 ——
     * 以后调整档位默认值不回溯已建任务
     */
    private String presetCode;

    /**
     * 超时秒数，0 表示取执行器声明值
     */
    private Integer timeoutSeconds;

    private Integer retryTimes;

    private Integer retryInterval;

    private String misfireStrategy;

    /**
     * 🔴 判定错过调度的阈值秒数，随档位联动，不能全局写死。
     * 理由见 {@code SmartJobPresetEnum.NORMAL} 的注释（与背压跳过直接冲突）
     */
    private Integer misfireThresholdSec;

    private String blockStrategy;

    /**
     * 环境标识：只有 env 匹配的节点才会抢这个任务
     */
    private String appEnv;

    private String alarmReceiver;

    /**
     * 连续失败次数，成功时清零，用于告警阈值
     */
    private Integer continuousFailCount;

    /**
     * 执行器在代码中不存在：该任务不会被执行，列表需标红
     */
    private Boolean handlerMissingFlag;

    /**
     * ONE_TIME 任务执行完置 true，列表默认折叠
     */
    private Boolean terminalFlag;

    private String ownerBizType;

    private String ownerBizCode;

    private String source;

    private Boolean manualModifiedFlag;

    private LocalDateTime lastExecuteTime;

    private Long lastExecuteLogId;

    private String remark;

    private Integer sort;

    private Boolean deletedFlag;

    private Boolean enabledFlag;

    private String updateName;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;
}
