package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权益发放记录 —— <b>同时就是「待领取」列表</b>。
 *
 * <h3>🔴 uk(member_id, entitlement_id, period_key) 是这个域的幂等根</h3>
 * job 每天扫全量，靠这个唯一键挡住重复生成，<b>不是「先查有没有再插」</b>：
 * 两个节点同时扫到同一个人，先查后插会双双通过 —— 而那是多发一份权益，
 * 且不报错、不留痕，只有月底对预算时才会发现多花了钱。
 *
 * <h3>⚠️ period_key 的长度由类型决定，两种不同是刻意的</h3>
 * <ul>
 *   <li>{@code BIRTHDAY} → {@code yyyy}，一年一次</li>
 *   <li>{@code MONTHLY} → {@code yyyyMM}，一月一次</li>
 * </ul>
 * 统一成 {@code yyyyMM} 的话，生日礼就变成<b>一年能领十二次</b>。
 *
 * <h3>为什么「等级」是生成时的快照</h3>
 * 月初你是白金就该给你白金的月度券，月中降级不该把已经给出去的收回。
 * 而更要紧的是反方向：<b>让用户看见「可领取」却领不了，比一开始就不给更伤</b> ——
 * 所以领取那一步不再判等级。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Data
@TableName("t_grade_entitlement_grant")
public class GradeEntitlementGrant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long entitlementId;

    /** 权益编码<b>快照</b>：配置改名之后，历史记录仍是当时那个 */
    private String entitlementCode;

    private Long memberId;

    /** 周期键：{@code BIRTHDAY} 用 yyyy，{@code MONTHLY} 用 yyyyMM */
    private String periodKey;

    /** 生成时的会员等级<b>快照</b> */
    private Integer gradeCode;

    /** 0-待领取, 1-已领取, 2-已过期。取值见 {@code EntitlementGrantStatus} */
    private Integer status;

    /** 领取截止时间。到点由 job 置为已过期 */
    private LocalDateTime expireTime;

    private LocalDateTime claimTime;

    /**
     * 发放单号：领取时作为 {@code AssetGrantCmd.bizRefId}。
     *
     * <p>🔴 它才是<b>跨域</b>防重的键 —— 本表的状态机只是第一道。
     * 真正兜底的是资产侧那三个唯一键（券 {@code uk_source}、
     * 现金 {@code UNIQUE(biz_ref_id, asset_type)}、实物 {@code uk_t_biz_phy_dlv_src}）。
     * 所以这个号必须在<b>生成时</b>就定下来并落库，不能领取时现生成：
     * 现生成的话，一次超时重试就会换一个号，两道防重全部失效。
     */
    private String grantBizId;

    /** 发放结果 / 失败原因，便于排查 */
    private String grantResult;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
