package sa.member.loginlog.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员登录日志（append-only，按月分区） 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberLoginLogQueryForm extends PageParam {

    @Schema(description = "会员号：精确匹配")
    private Long memberId;

    /**
     * 账号，模糊匹配。运营查登录记录时说得出口的是账号，不是 10 位会员号 ——
     * 只提供 memberId 查询的话，每次都得先去会员列表翻一次号码。
     */
    @Schema(description = "账号：模糊匹配")
    private String memberName;

    @Schema(description = "客户端IP（兼容IPv6，39位足够）")
    private String clientIp;

    @Schema(description = "发生时间（即登录时间）")
    private LocalDate createTimeBegin;

    @Schema(description = "发生时间（即登录时间）")
    private LocalDate createTimeEnd;

    @Schema(description = "状态：0-失败, 1-成功, 2-登出。⚠️与tLoginLog.loginResult取值相反")
    private Integer status;

    @Schema(description = "设备端：APP/H5/WECHAT/PC")
    private String deviceType;

    @Schema(description = "全链路追踪ID，对应 LogTraceFilter 的 MDC traceId")
    private String traceId;

}
