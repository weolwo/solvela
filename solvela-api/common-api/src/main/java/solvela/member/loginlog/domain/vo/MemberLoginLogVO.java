package solvela.member.loginlog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会员登录日志（append-only，按月分区） 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */

@Data
public class MemberLoginLogVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 / 昵称：join 出来的展示字段。
     * 列表里只显示 10 位会员号的话，运营认不出这是谁 —— 而登录日志的用途正是「看这个人的行为」。
     */
    @Schema(description = "账号")
    private String memberName;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "客户端IP（兼容IPv6，39位足够）")
    private String clientIp;

    @Schema(description = "IP归属地（ip2region 解析，SolvelaIpUtil 已有）")
    private String ipRegion;

    @Schema(description = "设备端：APP/H5/WECHAT/PC")
    private String deviceType;

    @Schema(description = "操作系统：iOS/Android/Windows")
    private String osName;

    @Schema(description = "浏览器：Chrome/Safari")
    private String browserName;

    @Schema(description = "状态：0-失败, 1-成功, 2-登出。⚠️与t_login_log.login_result取值相反")
    private Integer status;

    @Schema(description = "提示信息：成功可为空，失败写具体原因")
    private String remark;

    @Schema(description = "全链路追踪ID，对应 LogTraceFilter 的 MDC traceId")
    private String traceId;

    @Schema(description = "发生时间（即登录时间）")
    private LocalDateTime createTime;

}
