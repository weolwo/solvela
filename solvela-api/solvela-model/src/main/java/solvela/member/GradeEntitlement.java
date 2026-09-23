package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 等级权益配置 —— <b>会真的发出东西的那一张</b>。
 *
 * <h3>🔴 它和 {@link GradePrivilege} 是两张表，刻意的</h3>
 * <table border="1">
 *   <tr><th>表</th><th>回答什么</th><th>有没有引擎</th></tr>
 *   <tr><td>{@code t_grade_privilege}</td><td>用户在等级页<b>看见</b>自己在保什么</td><td>没有，纯展示</td></tr>
 *   <tr><td>{@code t_grade_entitlement}</td><td>到点<b>真的发</b>什么给谁</td><td>有引擎、有流水、有预算含义</td></tr>
 * </table>
 *
 * <p>{@code GradePrivilege} 的类注释写着：「谁都不要给它加生效引擎。那一天来临时，
 * 应该是<b>新立一个权益域</b>，而不是让这张展示表长出执行语义 ——
 * 展示与执行混在一张表里之后，<b>改一句文案就有可能改掉一条业务规则</b>。」
 * 这个类就是那一天。
 *
 * <h3>为什么不借道 t_prize_config 描述「发什么」</h3>
 * 那张表是<b>活动</b>维度的（{@code activity_code} 非空），而权益不属于任何活动。
 * 借道就得为权益造一个假活动，而那个假活动会出现在活动列表里、出现在活动的统计里，
 * 运营每次看到都要问一次「这是什么」。
 *
 * <p>所以这里的 {@code assetType / assetRef / assetName / quantity / amount}
 * 直接对齐 {@code AssetGrantCmd} —— 发放那一步本来就吃这几个字段。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Data
@TableName("t_grade_entitlement")
public class GradeEntitlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权益编码：10 位大写字母+数字，全局唯一（铁律 8） */
    private String entitlementCode;

    /** 权益名。会显示给用户，如「白金生日礼」 */
    private String entitlementName;

    /** {@code BIRTHDAY} 一年一次 / {@code MONTHLY} 一月一次。取值见 {@code EntitlementType} */
    private String entitlementType;

    /**
     * 需要的最低等级。
     *
     * <p>🔴 判据是 <b>{@code >=}</b> 不是 {@code =}：白金的月度券，钻石会员也该有。
     * 写成相等的话，用户升一级反而丢权益 —— 那是这套体系最不该出现的事。
     */
    private Integer minGrade;

    /** 资产类型：COUPON / BALANCE / SCORE，对齐 {@code PrizeTypeEnum} */
    private String assetType;

    /** COUPON 存券模板编码；BALANCE 存面额来源标识 */
    private String assetRef;

    /**
     * 展示名。
     *
     * <p>⚠️ 券名会直接显示给用户，<b>取不到时不要拿备注顶替</b> ——
     * {@code CouponAssetHandler} 里那段红字记的就是这个事故。
     */
    private String assetName;

    private Integer quantity;

    /** BALANCE 的单份面额；实发 {@code amount × quantity} */
    private BigDecimal amount;

    /**
     * 生成后多少天内可领。
     *
     * <p>🔴 待领取必须有有效期。一个从不打开 App 的人会攒下几十条永远不会被领的记录，
     * 而它们既占着预算口径、又让「有多少人享受了权益」这个数字彻底失真。
     */
    private Integer claimDays;

    private EnableStatusEnum status;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
