package solvela.event;

import solvela.enums.EventCategoryEnum;
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
    private LocalDateTime eventTime; // 发生时间
    private String remark;        // 备注

    /**
     * 事件大类，GlobalEventDispatcher 按它路由。由子类各自给定（一个子类一个大类），基类不存第二份 ——
     * 以前基类与 UserPrizeEvent 各声明了一个 String category，子类那份遮住了基类那份。
     */
    public abstract EventCategoryEnum getCategory();
}