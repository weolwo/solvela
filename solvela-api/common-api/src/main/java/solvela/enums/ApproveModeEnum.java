package solvela.enums;

import lombok.Getter;

@Getter
public enum ApproveModeEnum {

    AUTO(0, "自动"),
    MANUAL(1, "人工"),
    ;

    ApproveModeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;
}
