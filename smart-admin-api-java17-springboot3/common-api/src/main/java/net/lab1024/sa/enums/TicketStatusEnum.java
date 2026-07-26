package net.lab1024.sa.enums;

import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

@Getter
public enum TicketStatusEnum implements BaseEnum {
    WAIT(0, "待开奖"),
    FAILURE_MATCH(1, "未中奖"),
    SUCCESS_MATCH(2, "中奖"),
    ;

    TicketStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public boolean equalsValue(Object value) {
        return BaseEnum.super.equalsValue(value);
    }

    @Override
    public boolean equals(BaseEnum baseEnum) {
        return BaseEnum.super.equals(baseEnum);
    }
}
