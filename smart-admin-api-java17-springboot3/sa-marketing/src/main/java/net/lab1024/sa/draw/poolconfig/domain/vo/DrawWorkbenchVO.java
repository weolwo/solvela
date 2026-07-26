package net.lab1024.sa.draw.poolconfig.domain.vo;

import java.util.List;

/**
 * 抽奖工作台回显聚合VO：与聚合保存 DrawWorkbenchSaveForm 同构，前端拿到即可直接填回两个 Tab
 *
 * @param activityCode   活动编码
 * @param activityName   活动名称
 * @param activityStatus 活动状态：0-未开始, 1-上线, 2-下线
 * @param online         是否已上线：前端据此启用结构锁（服务端保存时会再算一遍，UI 只是防呆）
 * @param prizeItemList  Tab1 奖项物资库
 * @param poolList       Tab2 多奖池及其坑位
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawWorkbenchVO(String activityCode,
                              String activityName,
                              Integer activityStatus,
                              boolean online,
                              List<DrawWorkbenchItemVO> prizeItemList,
                              List<DrawWorkbenchPoolVO> poolList) {
}
