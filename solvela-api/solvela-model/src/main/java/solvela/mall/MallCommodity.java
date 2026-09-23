package solvela.mall;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商城-商品主表 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */

@Data
@TableName("t_mall_commodity")
public class MallCommodity {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品编码：10位大写字母+数字，全局唯一，创建后不可改
     */
    private String commodityCode;

    /**
     * 分类id
     */
    private Long categoryId;

    /**
     * 商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)
     */
    private String commodityType;

    /**
     * 资产引用：COUPON 存券模编码，PHYSICAL 为空。语义对齐 t_proposal_record.asset_ref
     *
     * <p>本类里带 {@code ALWAYS} 的都是<b>可空且能被运营清空</b>的列：商品类型从 COUPON
     * 改回 PHYSICAL 时 assetRef 必须真的变回 null，副标题/兑换须知/图文详情同理。
     * 默认的 NOT_NULL 策略会把这类 set 从 update 语句里静默去掉 —— 表现是「删不掉」。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String assetRef;

    /**
     * 商品名称
     */
    private String commodityName;

    /**
     * 副标题/一句话卖点
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String commodityIntro;

    /**
     * 封面主图 file_id（建议 800x800）
     */
    private Long coverFileId;

    /**
     * 图文详情，富文本HTML。禁止 base64 内联图片（对齐 t_activity_display.rule_content）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detailContent;

    /**
     * 兑换须知：券的核销说明、实物的发货时效等。C端下单页固定展示
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String exchangeNotice;

    /**
     * 支付方式：1-纯积分, 2-积分+现金
     */
    private MallPayTypeEnum payType;

    /**
     * 划线原价：仅前端展示「价值￥199」，纯积分商品可留 0
     */
    private BigDecimal originalPrice;

    /**
     * 基准兑换积分
     */
    private Integer pointsPrice;

    /**
     * 基准兑换现金：pay_type=1 时恒为 0
     */
    private BigDecimal cashPrice;

    /**
     * 限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月
     */
    private String limitPeriod;

    /**
     * 周期内单会员限兑件数：0-不限制
     */
    private Integer limitCount;

    /**
     * 可兑换的最低会员等级，对应 {@code t_member_grade.grade_code}。
     * {@code 0} = 不限，人人可兑。
     *
     * <h3>🔴 它是「兑不兑得了」，不是「看不看得见」</h3>
     * 不达标的商品在 C 端<b>照常展示</b>，只是标成「XX会员专享」且兑换按钮不可点 ——
     * 藏起来的话，用户永远不知道升到白金能换到什么，而
     * <b>「给用户一个够上去的理由」正是这套等级体系要换的东西</b>。
     * （任务中心那边是藏的，因为一个点不动的任务对用户没有任何吸引力，
     * 而一件看得见的商品有。两处判断不同是刻意的。）
     *
     * <p>⚠️ 展示归展示，<b>服务端必须照样拦</b>：列表藏了/标了都不等于买不了，
     * 直接打下单接口是绕不过去的那条路。守卫见 {@code MallGradeGate}。
     */
    private Integer minGrade;

    /**
     * 参不参与等级折扣：{@code 0} = 不参与，{@code 1}（默认）= 参与。
     *
     * <h3>⚠️ 它和 {@link #minGrade} 是两件不相干的事</h3>
     * {@code minGrade} 是「够不够格兑」，这一列是「兑的时候打不打折」。
     * 一件不限等级的商品照样可以打等级折扣（谁来都能兑，白金便宜些），
     * 一件白金专享的商品也可以不打折（本来就是给高等级的福利，不必再让一次）。
     *
     * <h3>🔴 默认参与，不是默认不参与</h3>
     * 默认不参与的话，运营配完折扣率会发现<b>一件商品都没变便宜</b>，
     * 然后来提一个「功能没生效」的 bug。默认参与，个别商品（成本价、秒杀品）再退出。
     *
     * <p>判断收在 {@code MallPricing.participates}，不在调用方 ——
     * 两条路各判一次，漏判的那条会给成本价商品打折，而那是真实的钱。
     */
    private Integer gradePriceFlag;

    /**
     * 上架开始时间：默认值代表不限。不是秒杀场次
     */
    private LocalDateTime startTime;

    /**
     * 上架结束时间：默认值代表不限。不是秒杀场次
     */
    private LocalDateTime endTime;

    /**
     * 状态：0-下架, 1-上架, 2-草稿。新建默认落草稿
     */
    private MallCommodityStatusEnum status;

    /**
     * 是否首页推荐：0-否, 1-是
     */
    private Boolean isHome;

    /**
     * 排序权重：从小到大
     */
    private Integer sort;

    /**
     * 累计已兑件数（各SKU之和的冗余，用于列表按热销排序）
     */
    private Integer soldCount;

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
