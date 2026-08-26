package solvela.draw.poolconfig.domain.command;

import java.math.BigDecimal;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

/**
 * 奖池配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
public class PrizePoolConfigAddCommand {

    /** 归属活动编码 */
    private String activityCode;

    /** 奖池编码：10 位大写字母+数字，全局唯一 */
    private String poolCode;

    /** 奖池名称 */
    private String poolName;

    /** 重置周期，天，周，月，活动期间 */
    private String resetPeriod;

    /** 抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio) */
    private Integer drawMode;

    /** 0关闭，1开启 */
    private Integer status;

}