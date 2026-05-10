package net.lab1024.sa.enums;

import lombok.Getter;

@Getter
public enum IssueStatusEnum {
    WAIT(0,"待开奖"),
    STAGED (1,"部分开奖"),
    OPENED(2,"已开奖"),
    ;

    IssueStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;
}
