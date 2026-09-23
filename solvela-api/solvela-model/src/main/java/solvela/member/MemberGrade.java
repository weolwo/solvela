package solvela.member;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 会员等级定义。
 *
 * <h3>🔴 等级是【配置】，不是枚举</h3>
 * 门槛、名称、档数都要能在后台改。写成 Java 枚举的话，运营加一档要发版 ——
 * 而加一档恰恰是这类体系最常见的运营动作。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Data
@TableName("t_member_grade")
public class MemberGrade {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 等级：0 起，数字越大越高。
     *
     * <p>🔴 {@code level=0} 那一行<b>必须存在</b>：新会员落在这儿，
     * 判级函数也拿它当兜底。删了的话一个没攒够任何成长值的人会判不出等级。
     */
    private Integer gradeCode;

    /** 等级名：普通会员 / 银卡 / 金卡 / 白金 / 钻石 */
    private String gradeName;

    /** 周期内成长值门槛（含）。{@code level=0} 必须为 0 */
    private Long threshold;

    /**
     * 商城积分折扣率 1-100，如 90 = 9 折。{@code null} 或 100 = 不打折。
     *
     * <h3>🔴 它会真的少收钱</h3>
     * 与同表的 {@code gradeName}／{@code iconFileId} 不是一类字段：那两个改了只是显示变了，
     * 这一个改了，<b>下一次兑换就按新折扣扣分</b>，全场所有参与等级折扣的商品一起变。
     * 所以它的取值有两道关：表单（{@code MemberGradeConfigForm}）与
     * {@code MallGradeDiscountResolver}，后者拦的是绕过表单直接改库那条路。
     *
     * <p>⚠️ 允许 null 而不是默认 100：null 是「这套还没开」，100 是「开了，这一档不打折」。
     * 业务上没区别，但运营看后台时有 —— 一片空白和一片 100 是两种意思。
     *
     * <p>⚠️ <b>只打积分，不打现金</b>。现金是真钱，打折牵扯支付金额、退款、发票和税务口径。
     * 规则本体在 {@code MallPricing}。
     *
     * <p>🔴 {@code ALWAYS}：这一列的 null 是<b>有业务含义的</b>（「取消折扣，改回不打折」）。
     * 默认的 NOT_NULL 策略会把这种「清空」从 SQL 里悄悄抹掉，
     * 表现是运营把 88 删成空、保存成功、刷新后 88 又回来了 ——
     * 和 {@code MallSku} 那三列踩的是同一个坑。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer pointsDiscount;

    /** 等级图标 file_id，走文件模块 */
    private Long iconFileId;

    /*
     * 🔴 benefits 这一列已废弃（2026-09-20）：权益拆到了 t_grade_privilege。
     *    一列 varchar(500) 装不下图标 file_id、跳转 url、排序、多语言，
     *    而运营迟早会要这四样 —— 那时再拆，得一边拆一边解析已经写进去的自由文本。
     *
     *    ⚠️ 别在这里加回一个「权益摘要」字段：那等于把刚拆开的东西又焊回来，
     *    而且摘要和 t_grade_privilege 一定会不同步。
     */

    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
