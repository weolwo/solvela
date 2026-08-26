package solvela.draw.poolconfig.domain.command;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 奖池配置 更新表单 —— <b>只允许改名称与限领重置周期</b>。
 *
 * <p>生成器原本还开放了 drawMode / scriptId / status 三个字段，已全部移除：
 * <ul>
 *   <li><b>drawMode</b>：后端从未读取过（全仓库 grep 确认）。选「按库存比例」照样按概率抽 ——
 *       是个假开关，留着只会让人以为配了就生效；</li>
 *   <li><b>scriptId</b>：同样从未被读取。2026-08-23 起该列已从 {@code t_prize_pool_config} 删除，
 *       奖池的准入脚本改为挂在 {@code t_script_ref} 上（挂载点 {@code PRIZE_POOL_ENTRY}），
 *       走 {@code ScriptRuntime} 执行；</li>
 *   <li><b>status</b>：奖池开关的唯一入口是 {@code offline} / {@code online} 两个接口。
 *       它们有并发闸门（{@code WHERE status = #{from}}），两个运营同时点时第二个人会拿到
 *       「状态已被其他人变更」而不是静默覆盖。走这个表单改 status 则两样都没有 ——
 *       同一件事留两条路径，其中一条还更弱，迟早从弱的那条出事。</li>
 * </ul>
 * 这里直接不定义字段，而不是在 Service 里忽略 —— 让越权意图在编译期就无处安放。
 *
 * <p>activityCode 与 poolCode 也刻意不在此列：poolCode 被坑位映射与抽奖流水引用，
 * 改了会让历史流水指向一个不存在的池。
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
public class PrizePoolConfigUpdateCommand {

    private Long id;

    /** 奖池名称 */
    private String poolName;

    /** 限领重置周期: DAY/WEEK/MONTH/ACTIVITY */
    private String resetPeriod;

}