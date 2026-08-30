package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资金流向，对齐 {@code t_member_asset_transaction.transaction_type}。
 *
 * <p>流水表里 {@code change_amount} 存的是<b>绝对值</b>，正负号完全由这一列表达 ——
 * 所以算余额、做报表时必须两列一起看，只看金额会把支出算成收入。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum TransactionTypeEnum implements BaseEnum {

    INCOME(1, "收入"),

    EXPENSE(2, "支出"),
    ;

    private final Integer value;

    private final String desc;
}
