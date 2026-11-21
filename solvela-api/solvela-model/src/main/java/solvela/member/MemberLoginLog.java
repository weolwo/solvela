package solvela.member;

import solvela.enums.LoginLogResultEnum;
import solvela.enums.DeviceTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员登录日志（append-only，按月分区） 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */

@Data
@TableName("t_member_login_log")
public class MemberLoginLog {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号
     */
    private Long memberId;

    /**
     * 客户端IP（兼容IPv6，39位足够）
     */
    private String clientIp;

    /**
     * IP归属地（ip2region 解析，SolvelaIpUtil 已有）
     */
    private String ipRegion;

    /**
     * 设备端：APP/H5/WECHAT/PC
     */
    private DeviceTypeEnum deviceType;

    /**
     * 操作系统：iOS/Android/Windows
     */
    private String osName;

    /**
     * 浏览器：Chrome/Safari
     */
    private String browserName;

    /**
     * 设备号，关联 t_device.device_id。
     *
     * <p><b>为空是正常的</b>：设备身份 2026-09 才上线，之前的登录没有这个信息；
     * 灰度期间老客户端也还没带设备令牌。查询侧不要把 NULL 当异常。
     *
     * <p>这一列的用途是<b>事后关联</b>：「一台设备碰过哪些账号」「这批号是不是
     * 同一批设备注册的」—— 它是将来判断「要不要买厂商指纹」的唯一依据。
     */
    private String deviceId;

    /**
     * 状态：0-失败, 1-成功, 2-登出。⚠️与t_login_log.login_result取值相反
     */
    private LoginLogResultEnum status;

    /**
     * 提示信息：成功可为空，失败写具体原因
     */
    private String remark;

    /**
     * 全链路追踪ID，对应 LogTraceFilter 的 MDC traceId
     */
    private String traceId;

    /**
     * 发生时间（即登录时间）
     */
    private LocalDateTime createTime;

}
