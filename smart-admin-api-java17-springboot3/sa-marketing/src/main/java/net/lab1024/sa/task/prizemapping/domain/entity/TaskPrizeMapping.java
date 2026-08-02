package net.lab1024.sa.task.prizemapping.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务阶段与奖励映射表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */

@Data
@TableName("t_task_prize_mapping")
public class TaskPrizeMapping {

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
     * 任务配置ID
     */
    private Long taskConfigId;

    /**
     * 阶段达标条件：如 {"min": 10, "max": 99} 或 {"action": "share"}
     */
    private String stageCondition;

    /**
     * 任务阶段：单次任务填1，阶梯任务填 1, 2, 3...
     */
    private Integer stageLevel;

    /**
     * 奖励编码
     */
    private String prizeCode;

    /**
     * 计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)
     */
    private String prizeMode;

    /**
     * 动态发奖策略JSON：如 {"amount": 20} 或 {"ratio": 0.05}
     */
    private String prizeStrategy;

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
