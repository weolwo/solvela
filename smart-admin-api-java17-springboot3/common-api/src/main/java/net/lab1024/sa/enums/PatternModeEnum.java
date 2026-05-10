package net.lab1024.sa.enums;

import lombok.Getter;

@Getter
public enum PatternModeEnum {
    FRONT_MATCH(0, "前匹配"),
    REAR_MATCH(1, "后匹配"),
    ;

    PatternModeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;
}
