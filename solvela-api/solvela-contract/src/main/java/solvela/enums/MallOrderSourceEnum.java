package solvela.enums;

/**
 * 商城订单来源：{@code t_mall_order.source_type}，按枚举名存，JSON 也是枚举名。
 *
 * <p>以前是 MallConst 里的两个字符串常量；下单处写的是裸字符串 "NORMAL"。
 */
public enum MallOrderSourceEnum {

    /** 日常兑换 */
    NORMAL,

    /** 限时抢购场次 */
    FLASH_SALE,
}
