package solvela.mall.commodity.domain.command;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品保存表单里的一行 SKU。
 *
 * <p><b>也没有 lockedStock / soldCount / availableStock</b>：这三个是运行态数据，
 * 只能由下单、履约、超时释放那几条链路改。放进表单等于允许运营用一次保存把已售清零。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySkuCommand {

    /** SKU id：新增行传 null */
    private Long id;

    /**
     * SKU 编码，规则同商品编码：运营可改、服务端判重、创建后不可改。
     * 履约链路按编码引用 SKU（{@code t_proposal_record.asset_ref} 的注释原文就是「券模/SKU」），
     * 改一次等于把已有的引用指向空。
     */
    private String skuCode;

    /**
     * 规格组合。<b>无规格商品也必须有一行</b>，这里传空 map，落库成 {@code {}} ——
     * DDL 注释写死了这条：没有「没有 SKU 的商品」，否则下单链路要为它写一套分支。
     *
     * <p>用 LinkedHashMap 保序：运营录的是「颜色 / 尺码」，回显时顺序变了会让人以为改错了东西。
     */
    private Map<String, String> skuAttrs = new LinkedHashMap<>();

    /** 该规格专属图 file_id：为空则用商品封面 */
    private Long skuCoverFileId;

    /**
     * 为空表示<b>继承主表基准价</b>，不是 0。
     * 0 是「免费兑换」的合法取值，用 0 当"未设置"就分不清「没填」和「真免费」了 —— DDL 里专门解释过。
     */
    private Integer skuPointsPrice;

    /** 本规格所需现金：为空则继承商品基准现金 */
    private BigDecimal skuCashPrice;

    /** 总库存：运营投放量，补货改这里 */
    private Integer totalStock;

    /** 状态：0-停用, 1-启用 */
    private Integer skuStatus;

    /** 排序：从小到大 */
    private Integer sort;
}
