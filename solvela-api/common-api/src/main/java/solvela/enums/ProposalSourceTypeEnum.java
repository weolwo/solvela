package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

import java.util.Arrays;

/**
 * 提案来源类型，对齐 {@code t_proposal_record.source_type}（varchar，故 value 是 String）。
 *
 * <p>🔴 <b>为什么新建这个枚举而不用 {@link EventTypeEnum}</b>：
 * 四个 {@code @PrizeStrategy} handler 此前都硬编码 {@code EventTypeEnum.LOTTERY_DRAW}，
 * 于是<b>任务发出去的奖在提案表里被记成「彩票抽奖」</b>，抽奖发的也一样。
 * 而 DDL 注释与 {@code ProposalRecordAddForm} 的 {@code @Schema} 写的一直是
 * {@code TASK / DRAW / MANUAL} —— <b>代码与契约从第一天起就对不上</b>，
 * 只是提案表的来源字段没人拿来做判据，所以一直没暴露。
 * {@code EventTypeEnum} 的取值（LOTTERY_DRAW / TASK_ASSIGN / MEMBER_UPGRADE）
 * 描述的是「事件类型」，与「提案来源」不是一回事，不该复用。
 *
 * <p><b>取值从活动类型推导</b>（见 {@code ProposalSourceResolver}）：
 * 「这个奖来自哪种玩法」本来就等于 {@code t_activity_config.activity_type}，
 * 不需要在 event/prize_log 上再加一个字段去传递，也就不需要动跨域契约。
 *
 * <p>⚠️ 字段必须叫 {@code value} 且不要手写 getValue()（铁律 12），写法对齐 solvela-base 的 GenderEnum。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum ProposalSourceTypeEnum implements BaseEnum {

    /**
     * 任务达标发奖
     */
    TASK("TASK", "任务"),

    /**
     * 奖池抽奖中奖
     */
    DRAW("DRAW", "抽奖"),

    /**
     * 彩票开奖中奖
     */
    LOTTERY("LOTTERY", "彩票"),

    /**
     * 人工发放（含 BASIC 活动 —— 它不挂玩法引擎，奖只可能是人工给的）
     */
    MANUAL("MANUAL", "人工"),
    ;

    private final String value;

    private final String desc;

    public static ProposalSourceTypeEnum resolve(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
