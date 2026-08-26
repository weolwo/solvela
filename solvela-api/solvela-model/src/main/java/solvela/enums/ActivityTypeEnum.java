package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

import java.util.Arrays;

/**
 * 活动类型，对齐 t_activity_config.activity_type（varchar，故 value 是 String）
 * <p>
 * ⚠️ 字段必须叫 value 且不要手写 getValue()：@CheckEnum 的校验器是用
 * map(BaseEnum::getValue) 建合法值白名单的，字段叫别的名字会让 Lombok 生成不出 getValue()，
 * 若再手写一个 return null 糊过编译，白名单就成了 [null,null,...]，任何取值都判非法。
 * TicketStatusEnum 曾因此让「按中奖状态筛选购彩记录」恒返回 400。写法对齐 solvela-base 的 GenderEnum。
 * <p>
 * BASIC 不是「没有类型」，而是「另一种类型」：只有活动外壳，可挂奖品（t_prize_config 的归属键
 * 是 activity_code 且与玩法无关）、可作为预算与统计的归集维度，只是不挂玩法引擎。
 * 有了它，「没有玩法配置」才不再同时意味着「半途放弃」和「本来就不需要」两件事。
 *
 * @Author weolwo
 * @Date 2026-07-29
 */
@Getter
@AllArgsConstructor
public enum ActivityTypeEnum implements BaseEnum {

    /**
     * 基础活动：仅活动外壳，不挂任何玩法引擎。
     * 因为没有玩法下游，它是唯一允许升级成其它类型的类型（升级不会产生孤儿数据）。
     */
    BASIC("BASIC", "基础活动", false),

    /**
     * 奖池抽奖：转盘/九宫格/盲盒
     */
    DRAW("DRAW", "奖池抽奖", true),

    /**
     * 任务驱动：签到/浏览/分享
     */
    TASK("TASK", "任务驱动", true),

    /**
     * FPE 彩票：加密发号/周期开奖
     */
    LOTTERY("LOTTERY", "FPE彩票", true),
    ;

    private final String value;

    private final String desc;

    /**
     * 是否挂载玩法引擎。
     * <p>
     * 这是「有没有第二步/要不要查玩法下游」的唯一判据，等价于前端 ACTIVITY_TYPE_ENUM 里的
     * component !== null。判据收在这一处，别在各处散写 if (BASIC.equals(type))（铁律 3）。
     */
    private final boolean gameplay;

    /**
     * 按 value 解析，非法值返回 null（由调用方决定是报错还是降级）。
     * <p>
     * 刻意不接受大小写变体：三个工作台的下拉是 activityType=DRAW 精确匹配的，
     * 这里若宽容地把 "draw" 也认了，反而会放进一个在工作台里永远查不到的活动。
     */
    public static ActivityTypeEnum resolve(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 是否为挂玩法的类型；类型非法时返回 false（当作没有玩法下游处理，由上游的 @CheckEnum 负责拦非法值）
     */
    public static boolean hasGameplay(String value) {
        ActivityTypeEnum type = resolve(value);
        return type != null && type.gameplay;
    }
}
