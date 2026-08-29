package solvela.member.loginlog.domain.query;

import solvela.enums.LoginLogResultEnum;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员登录日志分页查询的<b>领域参数</b>。形状与管理端的 {@code MemberLoginLogQuery} 目前一致，
 * 但<b>变更的理由不同</b>：Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}（全项目第一个改造的样板）。
 * 这里刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberLoginLogQuery extends PageParam {

    /** 会员号：精确匹配 */
    private Long memberId;

    /**
     * 账号，模糊匹配。运营查登录记录时说得出口的是账号，不是 10 位会员号 ——
     * 只提供 memberId 查询的话，每次都得先去会员列表翻一次号码。
     */
    private String memberName;

    /** 客户端IP（兼容IPv6，39位足够） */
    private String clientIp;

    /** 发生时间（即登录时间） */
    private LocalDate createTimeBegin;

    /** 发生时间（即登录时间） */
    private LocalDate createTimeEnd;

    /** 状态：0-失败, 1-成功, 2-登出。⚠️与tLoginLog.loginResult取值相反 */
    private LoginLogResultEnum status;

    /** 设备端：APP/H5/WECHAT/PC */
    private String deviceType;

    /** 全链路追踪ID，对应 LogTraceFilter 的 MDC traceId */
    private String traceId;

}
