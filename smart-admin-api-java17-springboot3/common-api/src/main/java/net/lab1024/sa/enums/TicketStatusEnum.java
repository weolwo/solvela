package net.lab1024.sa.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * 购彩记录的中奖状态，对齐 t_lottery_record.win_status
 *
 * ⚠️ 字段必须叫 value 而不是 code：{@link BaseEnum#getValue()} 是 @CheckEnum 校验器
 * 建立合法值白名单的唯一来源（EnumValidator 里 map(BaseEnum::getValue) 那一句），
 * 得让 Lombok 的 @Getter 直接生成出 getValue() 才对得上。
 *
 * 原实现字段名是 code，又手写了一个 getValue() 直接 return null，
 * 于是白名单变成 [null, null, null]，任何非空取值都判非法 —— 按中奖状态筛选购彩记录必定 400。
 * 那两个 equalsValue / equals 的 override 也只是原样调回父接口默认实现，纯噪音，一并删掉。
 *
 * @Author weolwo
 * @Date 2026-04-19
 */
@Getter
@AllArgsConstructor
public enum TicketStatusEnum implements BaseEnum {

    /**
     * 未开奖：号码已发出，所属期号尚未核销
     */
    WAIT(0, "未开奖"),

    /**
     * 未中奖：已核销，未命中任何奖级
     */
    FAILURE_MATCH(1, "未中奖"),

    /**
     * 已中奖：已核销并命中奖级，具体奖级见 prize_level
     */
    SUCCESS_MATCH(2, "已中奖"),
    ;

    private final Integer value;

    private final String desc;
}
