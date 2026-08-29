package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品状态，对齐 {@code t_mall_commodity.status}。
 *
 * <p>三态而不是「上架/下架」两态，所以没有复用 {@link EnableStatusEnum}：
 * {@link #DRAFT} 是<b>新建默认落点</b>（DDL 的 DEFAULT 就是 2），
 * 表示「还没配完，别放出去」；{@link #OFF} 是<b>上过架又撤下来</b>。
 * 两者对运营是不同的事，跟 {@link ActivityStatusEnum} 里 NOT_START 与 OFFLINE 的区别同构。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum MallCommodityStatusEnum implements BaseEnum {

    OFF(0, "下架"),

    ON(1, "上架"),

    /**
     * 草稿：新建默认落这里，对齐 DDL 的 DEFAULT 2
     */
    DRAFT(2, "草稿"),
    ;

    private final Integer value;

    private final String desc;
}
