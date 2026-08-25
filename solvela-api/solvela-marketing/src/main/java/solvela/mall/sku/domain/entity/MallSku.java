package solvela.mall.sku.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-SKU与库存 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */

@Data
@TableName("t_mall_sku")
public class MallSku {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 t_mall_commodity.id
     */
    private Long commodityId;

    /**
     * SKU编码：10位大写字母+数字，全局唯一
     */
    private String skuCode;

    /**
     * 规格组合：{"颜色":"星空灰","尺码":"XL"}。无规格商品填 {}
     */
    private String skuAttrs;

    /**
     * 该规格专属图 file_id：C端切换规格时换主图，为空则用商品封面
     *
     * <p>{@code ALWAYS}：这三列（专属图、两个继承价）的 null 是<b>有业务含义的</b> ——
     * 「清空专属图，改用商品封面」「取消覆盖价，改回继承主表」。
     * 默认的 NOT_NULL 策略会把这类「清空」从 SQL 里悄悄抹掉，表现是运营点了移除、
     * 保存成功、刷新后图又回来了。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long skuCoverFileId;

    /**
     * 本规格所需积分：为空则继承 t_mall_commodity.points_price
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer skuPointsPrice;

    /**
     * 本规格所需现金：为空则继承 t_mall_commodity.cash_price
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal skuCashPrice;

    /**
     * 总库存：运营投放量，恒定不变，补货改这里
     */
    private Integer totalStock;

    /**
     * 锁定库存：已下单未履约（仅 pay_type=2 会悬挂）
     */
    private Integer lockedStock;

    /**
     * 已售数量：履约成功累加
     */
    private Integer soldCount;

    /**
     * 可用库存（虚拟列，勿写入）
     *
     * <p>这是 MySQL 的 GENERATED ALWAYS 列，写它会直接报错。
     * 默认的 NOT_NULL 策略只是「碰巧」不写 —— 只要哪天有人把查出来的实体原样 updateById
     * （回显后改一个字段再存回去是最自然的写法），availableStock 就是非 null 的，
     * 整条 update 当场失败。NEVER 是把这条堵死，不是保险。
     */
    @TableField(value = "available_stock", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer availableStock;

    /**
     * 状态：0-停用, 1-启用
     */
    private Integer skuStatus;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
