package net.lab1024.sa.base.common.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BizErrorCode implements ErrorCode {

    AMOUNT_MUST_BE_GREATER_THAN_ZERO(50000, "入账金额必须大于0"),
    ACCOUNT_BALANCE_CHANGED(50001, "账户余额变动中，请重试"),

    ;

    private final int code;

    private final String msg;

    private final String level;

    BizErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.level = LEVEL_BIZ;
    }
}
