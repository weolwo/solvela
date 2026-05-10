package net.lab1024.sa.enums;

import lombok.Getter;

@Getter
public enum TicketStatusEnum {
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
}
