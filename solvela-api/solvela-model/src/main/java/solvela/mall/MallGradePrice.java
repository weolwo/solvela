package solvela.mall;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商城-等级覆盖价：<b>这一件、这一档，就这个数</b>。
 *
 * <h3>它和等级折扣率是两种东西</h3>
 * {@code t_member_grade.points_discount} 是全场普惠（白金一律 9.2 折），
 * 这张表是单品特价。命中覆盖价就<b>不再打折</b> —— 两个都算一遍等于打了两次折，
 * 而运营配「白金特价 888」的意思是 888，不是 888 再打 9.2 折。
 *
 * <h3>🔴 配在这里的每一行都会真的少收钱</h3>
 * 与商品名、图片不是一类：改一行，下一次兑换就按新价扣分。
 * 所以它的权限点跟着商品编辑走，而不是跟着「看商品列表」走。
 *
 * @author alaric
 * @date 2026-09-23
 */
@Data
@TableName("t_mall_grade_price")
public class MallGradePrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_mall_commodity.id */
    private Long commodityId;

    /**
     * 关联 t_mall_sku.id；{@code 0} = 整个商品。
     *
     * <p>🔴 <b>是 0 不是 null。</b>唯一键是 {@code (commodity_id, sku_id, grade_code)}，
     * 而 MySQL 的唯一索引<b>不约束 NULL</b> —— 两行 {@code (3, NULL, 4)} 能同时存在，
     * 于是「整个商品对钻石的价」会有两条，查出来是哪条取决于存储顺序。
     * 不报错、不冲突，只是价格随机。
     *
     * <p>⚠️ 解析顺序刻意抄了现有价格模型：规格覆盖价 &gt; 商品覆盖价，
     * 就像 {@code sku_points_price} &gt; {@code points_price}。
     * 这个库里「规格盖商品」已经是一条认识，不该另发明一套。
     */
    private Long skuId;

    /** 对应 t_member_grade.grade_code。<b>不允许 0 档</b>（校验在 MallGradePriceService） */
    private Integer gradeCode;

    /**
     * 这一档就这个价（积分）。
     *
     * <p>⚠️ {@code 0} 是合法的 —— 这一档免费兑换。
     * 与 {@code sku_points_price} 那条铁律同源，区别是那一列用 NULL 表示「没配」，
     * 而这张表<b>整行存在与否</b>就是「配没配」，所以这里 NOT NULL，
     * 不需要再拿 0 去兼职「未设置」。
     */
    private Integer pointsPrice;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
