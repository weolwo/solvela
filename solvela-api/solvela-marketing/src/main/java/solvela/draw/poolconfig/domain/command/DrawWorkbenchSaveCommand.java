package solvela.draw.poolconfig.domain.command;

import lombok.Data;

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
    private List<DrawWorkbenchPoolItemCommand> prizeItemList;

    /** Tab2 奖池列表（多池） */
    private List<DrawWorkbenchPoolCommand> poolList;
}
