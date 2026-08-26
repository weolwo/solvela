package solvela.member.loginlog.domain.dto;


import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员登录日志列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MemberLoginLogDTO {


    private Long id;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 / 昵称：join 出来的展示字段。
     * 列表里只显示 10 位会员号的话，运营认不出这是谁 —— 而登录日志的用途正是「看这个人的行为」。
     */
    private String memberName;

    /** 昵称 */
    private String nickname;

    /** 客户端IP（兼容IPv6，39位足够） */
    private String clientIp;

    /** IP归属地（ip2region 解析，SolvelaIpUtil 已有） */
    private String ipRegion;

    /** 设备端：APP/H5/WECHAT/PC */
    private String deviceType;

    /** 操作系统：iOS/Android/Windows */
    private String osName;

    /** 浏览器：Chrome/Safari */
    private String browserName;

    /** 状态：0-失败, 1-成功, 2-登出。⚠️与t_login_log.login_result取值相反 */
    private Integer status;

    /** 提示信息：成功可为空，失败写具体原因 */
    private String remark;

    /** 全链路追踪ID，对应 LogTraceFilter 的 MDC traceId */
    private String traceId;

    /** 发生时间（即登录时间） */
    private LocalDateTime createTime;

}
