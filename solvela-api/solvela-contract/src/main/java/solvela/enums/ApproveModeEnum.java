package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 奖品的审批模式，对齐 {@code t_prize_config.approve_mode}。
 *
 * <p>决定中奖之后是直接发，还是先落一条待审批记录等运营点头。
 *
 * <p>⚠️ 字段必须叫 {@code value} 而不是 code：{@link BaseEnum#getValue()} 是 @CheckEnum
 * 校验器建立合法值白名单、以及 MyBatis 枚举 TypeHandler 读写数据库的唯一来源。
 * 本枚举 2026-08-29 之前叫 {@code getCode()} 且没有实现 {@link BaseEnum}，
 * 于是整套枚举设施（@CheckEnum / @EnumSerialize / IEnum 映射）对它全都不生效。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum ApproveModeEnum implements BaseEnum {

    AUTO(0, "自动免审"),

    MANUAL(1, "人工审批"),
    ;

    private final Integer value;

    private final String desc;
}
