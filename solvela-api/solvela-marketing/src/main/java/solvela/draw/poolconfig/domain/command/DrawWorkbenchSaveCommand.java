package solvela.draw.poolconfig.domain.command;

import lombok.Data;

import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

import java.util.List;

/**
 * 抽奖工作台 聚合保存表单（主子表：t_prize_pool_item + t_prize_pool_config + t_pool_prize_mapping）
 * 结构与前端契约一致：{ activityCode, prizeItemList, poolList }
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchSaveCommand {

    /** 活动编码 */
    private String activityCode;

    /** Tab1 奖项物资列表 */
    /**
     * 抽奖配置编码。<b>已有配置时服务端忽略本字段</b> —— 它是脚本挂载的引用键，
     * 改了等于把已有挂载指向一个不存在的对象，而挂载表不会跟着动
     */
    private String drawCode;

    /** 抽奖名称 */
    private String drawName;

    /**
     * 重置周期：DAY / WEEK / MONTH / ACTIVITY。
     *
     * <p>它同时决定两件事：奖项「单人限领次数」的计数桶，以及统计
     * 「本轮已经抽了几次」时的时间下界。两者由同一个开关驱动
     */
    private String resetPeriod;

    /** 抽奖算法。目前只有「按概率」是真的实现 */
    private DrawModeEnum drawMode;

    /** 抽奖开关：关闭后该活动的抽奖请求直接被拒，脚本不会执行 */
    private EnableStatusEnum drawStatus;

    private List<DrawWorkbenchPoolItemCommand> prizeItemList;

    /** Tab2 奖池列表（多池） */
    private List<DrawWorkbenchPoolCommand> poolList;
}
