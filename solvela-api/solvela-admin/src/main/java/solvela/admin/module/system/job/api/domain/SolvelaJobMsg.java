package solvela.admin.module.system.job.api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 定时任务 发布/订阅消息对象
 *
 * @author huke
 * @date 2024/6/20 21:10
 */
@Data
public class SolvelaJobMsg {

    /**
     * 消息id 无需设置
     */
    private String msgId;

    /**
     * 任务id
     */
    private Integer jobId;

    /**
     * 任务参数
     */
    private String param;

    /**
     * 执行记录 id：终止操作用。
     *
     * <p>终止是广播出去的 —— 只有真正持有那个 Future 的节点会响应，
     * 其余节点查无此条、安静忽略。这比维护一张全局「谁在跑什么」的表简单得多
     */
    private Long logId;

    /**
     * 消息类型
     */
    private MsgTypeEnum msgType;

    /**
     * 更新人
     */
    private String updateName;

    @Getter
    @AllArgsConstructor
    public enum MsgTypeEnum implements BaseEnum {

        /**
         * 1 更新任务
         */
        UPDATE_JOB(1, "更新任务"),

        EXECUTE_JOB(2, "执行任务"),

        /**
         * 终止正在执行的任务。
         *
         * <p>⚠️ 能不能真的终止取决于执行器有没有响应中断 ——
         * {@code cancel(true)} 本质是 interrupt。界面必须如实说「已发出中断信号」
         */
        TERMINATE_JOB(3, "终止执行"),

        ;

        private final Integer value;

        private final String desc;
    }
}
