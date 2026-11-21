package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import solvela.enums.DeviceTypeEnum;

/**
 * 设备注册表（服务端签发，不带 member_id） 实体类。
 *
 * <h3>🔴 本表刻意没有 member_id</h3>
 * 一台设备登多个号是<b>要发现的信号</b>，不是要建的约束。
 * 「这台设备下有哪些账号」从 {@code t_member_login_log.device_id} 聚合 ——
 * 那张表已经按月分区、已经有 member_id，补一列就够了，不必单建关系表。
 *
 * <p>包在 {@code solvela.member} 下而不是自成一域，与 {@code DumpSchema} 把 {@code t_device}
 * 归进「会员域」是同一个理由：它的 Dao 和读者都在这儿，查询时也几乎总是和
 * {@code t_member_login_log} 一起出现。<b>包的归属不等于表的语义</b>，后者见上一段。
 *
 * @Date 2026-09-08
 */
@Data
@TableName("t_device")
public class Device {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 服务端签发的设备号，不接受客户端自报。32 位小写 hex
     */
    private String deviceId;

    /**
     * 设备端：APP/H5/WECHAT/PC。与 t_member_login_log.device_type 同名同口径
     */
    private DeviceTypeEnum deviceType;

    /**
     * 品牌型号，客户端自报，仅供人工排查
     */
    private String model;

    /**
     * 系统版本：区分 iOS/Android 靠它，device_type 只到端
     */
    private String osVersion;

    /**
     * 应用版本
     */
    private String appVersion;

    /**
     * 签发时IP（兼容IPv6，39位足够）
     */
    private String registerIp;

    /**
     * IP归属地（ip2region 解析，SolvelaIpUtil 已有）
     */
    private String registerRegion;

    /**
     * 签发时用的HMAC密钥版本：密钥泄露要能轮换，而验签得知道该用哪一把
     */
    private Integer keyVersion;

    /**
     * 可信度：0-仅自报, 1-验证码通过, 2-厂商证明通过（暂无厂商，先留档位）
     */
    private Integer attestLevel;

    /**
     * 处置档：0-正常, 1-观察（登录需验证码）, 2-封禁
     */
    private Integer status;

    /**
     * 处置原因：给客服看的人话
     */
    private String remark;

    /**
     * 人工处置的操作人：status=2 时必填，用于追溯。自动降档时为空
     */
    private String operator;

    /**
     * 最后活跃时间，节流写（>1h 才更新）
     */
    private LocalDateTime lastActiveTime;

    /**
     * 创建时间（即签发时间）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
