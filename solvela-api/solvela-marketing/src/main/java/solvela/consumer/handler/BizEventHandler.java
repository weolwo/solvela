package solvela.consumer.handler;

import solvela.event.BaseBizEvent;

/**
 * 重型事件处理器的统一接口规范
 *
 * @param <T> 具体的事件类型
 */
public interface BizEventHandler<T extends BaseBizEvent> {
    void handle(T event);
}