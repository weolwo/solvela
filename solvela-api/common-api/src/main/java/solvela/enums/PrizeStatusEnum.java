package solvela.enums;

import lombok.Getter;

@Getter
public enum PrizeStatusEnum {
    //0-等待, 1-成功, 2-失败
    Wait(0,"等待"),
    success(1,"成功"),
    failure(2,"失败"),
    ;

    PrizeStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private  Integer code;

    private String desc;
}
