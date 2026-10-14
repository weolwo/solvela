package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;

import java.time.LocalDateTime;

/**
 * 会员风控决策流水：<b>一次登录尝试一行</b>（按月分区，除标注三列外 append-only）。
 *
 * <p>🔴 主键是 {@code (id, create_time)}：按 id 定位一行时<b>一定要同时带 create_time</b>，
 * 否则每个分区都要各探一次索引。
 *
 * @Date 2026-10-15
 */
@Data
@TableName("t_member_risk_event")
public class MemberRiskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private MemberRiskEventTypeEnum eventType;

    /** 二次验证指向签票的那次登录 */
    private Long refEventId;

    /** 查无此人时为 null */
    private Long memberId;

    /**
     * 登录身份的 PiiHasher 摘要，64 位 hex。
     *
     * <p>🔴 列是 {@code binary(32)}，按 {@code PiiHasher} 类注释的约定<b>二进制不进 Java 类型系统</b>：
     * 写入走 {@code MemberRiskEventDao.insertEvent} 里的 {@code UNHEX}，读出用 {@code HEX(identity_hash)}。
     * 所以这里标 {@code exist = false}，不让 MyBatis-Plus 的通用 insert 碰它。
     */
    @TableField(exist = false)
    private String identityHashHex;

    /** 见 {@code MemberLoginType} 的序号：1-手机号密码, 2-邮箱密码, 3-邮箱验证码 */
    private Integer loginType;

    private String deviceId;

    /** 0-无设备, 1-新设备, 2-老交情, 3-验证过, 4-注册设备 */
    private Integer deviceTrust;

    /** 0-陌生, 1-宽松熟悉, 2-严格熟悉 */
    private Integer ipFamiliar;

    /** 0-活跃, 1-沉睡 */
    private Integer dormant;

    private String clientIp;

    private String ipRegion;

    /** 1 = 本次请求带了有效滑块通行票 */
    private Integer captchaPassed;

    private Integer preScore;

    private Integer postScore;

    private Integer shadowScore;

    private MemberRiskDecisionEnum decision;

    private MemberRiskDecisionEnum shadowDecision;

    /** 1-邮箱, 2-短信 */
    private Integer verifyChannel;

    private MemberRiskOutcomeEnum outcome;

    /** 1-确认本人, 2-确认被盗 */
    private Integer label;

    private Integer labelSource;

    private LocalDateTime labelTime;

    private Integer costMs;

    private String traceId;

    /** 🔴 由写入方显式赋值（秒级），命中明细用同一个值，两表才能按 (id, create_time) 关联 */
    private LocalDateTime createTime;
}
