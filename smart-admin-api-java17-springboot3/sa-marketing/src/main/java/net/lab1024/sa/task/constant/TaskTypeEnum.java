package net.lab1024.sa.task.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

import java.util.Arrays;

/**
 * 任务进度累积类型，对齐 t_task_template.task_type（varchar，故 value 是 String）。
 *
 * <p><b>这是策略工厂的 key，必须少而稳。</b>
 * 它区分的是「进度如何累积」—— 也正是唯一真正需要不同 Java 代码路径的维度。
 * 一旦有人提出「加个 task_type 来支持新玩法」，那就是设计跑偏的信号：
 * 新玩法应该体现在 rule_config（第②层），不是体现在这里（第①层）。
 * 三层分离见 docs/任务中台-改进技术方案.md §3.2。
 *
 * <p>⚠️ 字段必须叫 {@code value} 且不要手写 getValue()：{@code @CheckEnum} 的校验器是用
 * {@code map(BaseEnum::getValue)} 建合法值白名单的，字段叫别的名字会让 Lombok 生成不出 getValue()，
 * 若再手写一个 return null 糊过编译，白名单就成了 [null,null,...]，任何取值都判非法。
 * TicketStatusEnum 曾因此让「按中奖状态筛选购彩记录」恒返回 400。写法对齐 sa-base 的 GenderEnum。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum implements BaseEnum {

    /**
     * 单次节点型：一次性达成，0 -> 1。如「完善资料」「首次下单」
     */
    SIMPLE("SIMPLE", "单次节点型", false),

    /**
     * 计次型：每次事件 +1。如「累计签到 7 天」「下单 3 次」
     */
    COUNT("COUNT", "计次型", false),

    /**
     * 计额型：每次事件 + event.amount。如「累计消费满 500」
     */
    AMOUNT("AMOUNT", "计额型", false),

    /**
     * 连续型：断档清零。如「连续签到 7 天」
     *
     * <p>⚠️ 它与 COUNT 不只是「多记一个 lastHitDate」的差别，两者的<b>周期模型不同</b>：
     * STREAK 恒用 {@code period_key = NONE}（整个周期一条记录，连续数才有地方累加），
     * 因此 uk_t_tsk_rec_mbr_cfg_prd 不再承担日内幂等，改由 t_task_record_flow 承担。
     * 详见方案 §2.1.1，动它之前必读。
     */
    STREAK("STREAK", "连续型", true),
    ;

    private final String value;

    private final String desc;

    /**
     * 是否必须走「读-改-写 + 乐观锁」推进。
     *
     * <p>只有 STREAK 为 true：它要先读 lastHitDate 才知道本次是「清零再+1」还是「+1」。
     * 其余三类走条件更新原子累加（一条 SQL、零冲突、零重试）。
     * 判据收在这一处，别在各处散写 {@code if (STREAK.equals(type))}（铁律 3）。
     */
    private final boolean readModifyWrite;

    /**
     * 按 value 解析，非法值返回 null（由调用方决定是报错还是降级）。
     *
     * <p>刻意不接受大小写变体：task_type 是服务端从模板强制覆写进 rule_config 的
     * （TaskConfigService.wizardSubmit），不存在用户手输的路径，宽容匹配只会掩盖数据问题。
     */
    public static TaskTypeEnum resolve(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
