package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息接收记录的处理状态，对齐 {@code t_mq_message_log.status}。
 *
 * <h3>🔴 幂等的判据是 {@link #SUCCESS}，不是「有没有这行」</h3>
 * 见过但没处理成功的消息<b>必须允许再来一次</b> —— 否则死信重投、后台点重试
 * 都会被自己的幂等记录挡住，而那正是失败最需要重来的时候。
 *
 * @Date 2026-08-30
 */
@Getter
@AllArgsConstructor
public enum MqMessageStatusEnum implements BaseEnum {

    /** 已接收，还没处理完。进程在这中间挂了，行就停在这里 —— 重投任务扫的就是它 */
    RECEIVED(0, "已接收"),

    /** 处理成功。<b>只有这个状态才让重复投递跳过</b> */
    SUCCESS(1, "处理成功"),

    /** 处理失败，原因在 fail_reason 里。允许重来 */
    FAILED(2, "处理失败"),
    ;

    private final Integer value;

    private final String desc;
}
