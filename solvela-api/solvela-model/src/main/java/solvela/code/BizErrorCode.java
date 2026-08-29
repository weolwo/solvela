package solvela.code;

import lombok.Getter;

@Getter
public enum BizErrorCode implements ErrorCode {

    AMOUNT_MUST_BE_GREATER_THAN_ZERO("入账金额必须大于0"),
    ACCOUNT_BALANCE_CHANGED("账户余额变动中，请重试"),
    BALANCE_NOT_ENOUGH("余额不足"),

    ;

    private final String msg;

    BizErrorCode(String msg) {
        this.msg = msg;
    }
}
