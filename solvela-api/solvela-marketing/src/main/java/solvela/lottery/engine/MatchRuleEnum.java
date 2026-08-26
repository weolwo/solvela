package solvela.lottery.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 奖级匹配规则，对齐 t_lottery_prize_rule.match_rule
 *
 * 取代了此前零引用的 PatternModeEnum(0-前匹配/1-后匹配)——那是旧设计残留，
 * 与本枚举属于两套并存语义，已随 P0 清场删除。
 *
 * ⚠️ 字段必须叫 value：BaseEnum.getValue() 是 @CheckEnum 建合法值白名单的唯一来源，
 * 得让 Lombok 的 @Getter 直接生成出来。TicketStatusEnum 曾因字段名为 code 又手写
 * getValue(){return null;}，导致白名单全 null、任何取值都判非法。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Getter
@AllArgsConstructor
public enum MatchRuleEnum implements BaseEnum {

    /**
     * 全号匹配：号码与开奖号码完全相同
     */
    EXACT("EXACT", "全号匹配"),

    /**
     * 尾号匹配：末 matchLength 位相同
     */
    TAIL("TAIL", "尾号匹配"),

    /**
     * 首号匹配：首 matchLength 位相同
     */
    HEAD("HEAD", "首号匹配"),
    ;

    private final String value;

    private final String desc;

    /**
     * 字符串转枚举，非法值返回 null 由调用方决定如何处理。
     *
     * ⚠️ 存在的意义：本项目已经三次踩过「枚举键 map 用字符串查」的坑
     * （GlobalEventDispatcher / PrizeStrategyFactory / AssetStrategyFactory）——
     * Map.get(Object) 不做类型检查，用 String 去查 Map&lt;XxxEnum,?&gt; 编译通过、运行恒 null。
     * 凡是从 DB 拿到字符串要走枚举分支的地方，一律先经过这里显式转换。
     */
    public static MatchRuleEnum resolve(String value) {
        if (value == null) {
            return null;
        }
        for (MatchRuleEnum rule : values()) {
            if (rule.value.equalsIgnoreCase(value.trim())) {
                return rule;
            }
        }
        return null;
    }
}
