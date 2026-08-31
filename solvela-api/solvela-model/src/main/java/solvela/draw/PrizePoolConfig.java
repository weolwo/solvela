package solvela.draw;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.PrizePoolStatusEnum;

import java.time.LocalDateTime;

/**
 * 奖池配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_pool_config")
public class PrizePoolConfig {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 奖池唯一编码 (如: VIP_POOL)
     */
    private String poolCode;

    /**
     * 所属抽奖配置编码，关联 {@code t_draw_config.draw_code}。
     *
     * <p>存量行可能为空（这一列是后加的）。为空时按 activity_code 回退查抽奖配置 ——
     * 一个活动一套抽奖，两条路等价。见 {@code DrawConfigService#getByPool}。
     */
    private String drawCode;

    /**
     * 奖池名称
     */
    private String poolName;

    /*
     * 🔴 reset_period 与 draw_mode 已于 2026-08-31 搬到 t_draw_config，列也已删除。
     *
     * 它们是<b>玩法级</b>参数（一套抽奖一个节奏），不是某个奖池自己的事。
     * 重置周期现在同时决定两件事：单人限领的计数桶，以及统计「本轮已抽几次」时的时间下界。
     *
     * ⚠️ 别在这里加回来：MyBatis-Plus 按实体字段拼 SELECT 列清单，
     * 加一个库里没有的字段 = 这张表的每一次查询都报 Unknown column，
     * 而报错点在 DAO 里，离病因很远。
     */

    /**
     * 0关闭，1开启
     */
    private PrizePoolStatusEnum status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
