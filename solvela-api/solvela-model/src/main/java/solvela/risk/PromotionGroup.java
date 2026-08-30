package solvela.risk;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 优惠配置分组 实体类。
 *
 * <h3>它解决的是什么</h3>
 * 预算与风控是<b>按资产类型分开算</b>的（{@code used_amount} 混不了积分和元），
 * 所以一个活动有几种奖励类型，就得有几条 {@code t_promotion_config}。
 * 但那几条之间通常只有资产类型和预算不同，风控参数一模一样 ——
 * 运营的真实痛点不是「要建 N 条」，是「N 条里 90% 的字段要重填 N 遍」。
 *
 * <p>分组把那 N 条收进一个入口，工作台一页配完。
 * <b>它只是容器和配置入口，不参与任何运行态判定</b>：发奖链路从头到尾不认识分组，
 * 读的还是具体那条 {@code t_promotion_config}。
 *
 * <h3>🔴 为什么没有 activity_code</h3>
 * 优惠配置是<b>可跨活动复用的预算池</b>，表上从来没有活动关联列。
 * 给分组加一个就等于把这个定位打破：同一套风控参数想给两个活动用，
 * 就得复制一份，回到了这层封装本来要解决的问题。
 * 想表达「这是中秋活动用的」，写在 {@code group_name} 里即可。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
@TableName("t_promotion_group")
public class PromotionGroup {

    /** 分组ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分组编码：10 位大写字母+数字，全局唯一 */
    private String groupCode;

    /** 分组名称，如「2026中秋活动优惠配置」 */
    private String groupName;

    /** 备注 */
    private String remark;

    /**
     * 状态：0-停用, 1-启用。<b>这是组内所有配置的主开关</b>。
     *
     * <h3>不变量：组停用 ⇒ 组内配置全部停用</h3>
     * 关掉分组会连带把组内每一条 {@code t_promotion_config} 一起停用 ——
     * 出事时能一键停掉整个活动的发放，是运营最需要的能力，
     * 而「组关了、子配置还在发」是这里最不能出现的状态。
     *
     * <p>反过来<b>不对称</b>：开启分组时必须<b>显式选择</b>要启用哪几种资产类型。
     * 因为「关之前哪些是开的」这个信息在关的那一刻就被覆盖掉了，
     * 与其猜一个（猜错就是把本来停发的类型重新放出去），不如让人当场确认一次。
     *
     * <p>这条不变量在两个入口都要维持：{@code PromotionGroupService.updateStatus}
     * 的开关动作，以及 {@code workbenchSave} —— 保存一个停用中的分组时，
     * 子项一律落停用，不管前端那几张卡上的开关是什么状态。
     */
    private EnableStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
