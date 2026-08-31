package solvela.draw.poolconfig.domain.dto;

import solvela.enums.ActivityStatusEnum;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

import java.util.List;

/**
 * 抽奖工作台回显聚合VO：与聚合保存 DrawWorkbenchSaveCommand 同构，前端拿到即可直接填回各个 Tab。
 *
 * <p>未配置过的活动返回一个「空壳」而不是报错，前端据此进入「从零配置」态 ——
 * 与彩票工作台的处理一致。
 *
 * @param activityCode   活动编码
 * @param activityName   活动名称
 * @param activityStatus 活动状态：0-未开始, 1-上线, 2-下线
 * @param online         是否已上线：前端据此启用结构锁（服务端保存时会再算一遍，UI 只是防呆）
 * @param drawCode       抽奖配置编码；<b>未配置时返回一个预生成的可用编码</b>，运营可直接用
 * @param drawName       抽奖名称
 * @param resetPeriod    重置周期：DAY / WEEK / MONTH / ACTIVITY
 * @param drawMode       抽奖算法
 * @param drawStatus     抽奖开关；未配置时为 null
 * @param drawConfigured 是否已有抽奖配置：false 表示这个活动还没配过抽奖，前端走「从零配置」
 * @param drawCodeLocked 抽奖编码是否已冻结：已有配置即冻结，因为脚本挂载引用的就是它
 * @param prizeItemList  Tab1 奖项物资库
 * @param poolList       Tab2 多奖池及其坑位
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawWorkbenchDTO(String activityCode,
                               String activityName,
                               ActivityStatusEnum activityStatus,
                               boolean online,
                               String drawCode,
                               String drawName,
                               String resetPeriod,
                               DrawModeEnum drawMode,
                               EnableStatusEnum drawStatus,
                               boolean drawConfigured,
                               boolean drawCodeLocked,
                               List<DrawWorkbenchItemDTO> prizeItemList,
                               List<DrawWorkbenchPoolDTO> poolList) {
}
