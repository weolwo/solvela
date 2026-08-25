package solvela.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseBizEvent implements Serializable {
    private String eventId;       // 事件唯一标识
    private String category;      // 事件大类：PRIZE(奖励), TASK(任务), MEMBER(会员)
    private String eventType;     // 具体子类：LOTTERY_WIN, TASK_ASSIGN, MEMBER_UPGRADE
    private LocalDateTime eventTime; // 发生时间
    private String remark;        // 备注
}