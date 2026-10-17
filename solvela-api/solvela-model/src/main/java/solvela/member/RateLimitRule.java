package solvela.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关限流规则：场景给默认值，接口按需覆盖。后台维护，经 Redis 快照下发到网关
 * （docs/架构/网关阶梯限流-技术方案.md §6.1）。
 *
 * <p>和 {@link SecurityList} 一样放在会员域的模型里：读写它的代码在会员域（后台进程里跑），
 * 网关不连库，只认快照。
 *
 * @Date 2026-10-17
 */
@Data
@TableName("t_rate_limit_rule")
public class RateLimitRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1-场景（默认值）, 2-接口（覆盖） */
    private Integer targetType;

    /** 场景编码，或接口键（{@code POST /activity/{activityCode}/draw}） */
    private String target;

    /** 0-全局, 1-IP, 2-账号, 3-设备, 4-IP段 */
    private Integer dimension;

    /** 10 秒令牌桶容量 */
    private Integer burst;

    /**
     * 🔴 三个窗口阈值必须显式写列名：驼峰转下划线不会在数字前断开，
     * {@code limit3m} 会被映射成不存在的 {@code limit3m} 列，读出来恒为 null。
     */
    @TableField("limit_3m")
    private Integer limit3m;

    @TableField("limit_5m")
    private Integer limit5m;

    @TableField("limit_10m")
    private Integer limit10m;

    /** 可选长窗口：1h / 1d；null = 不启用 */
    private String longWindow;

    private Integer limitLong;

    /** 0-关闭, 1-陪跑, 2-生效 */
    private Integer mode;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
