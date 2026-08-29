package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用的「停用 / 启用」二值状态。
 *
 * <p>库里有一大批列是这同一个概念：{@code t_prize_config.status}、{@code t_script.status}、
 * {@code t_task_event.status}、{@code t_promotion_config.status} …… 取值和措辞完全一致，
 * 各建一个枚举只是把同一份知识抄很多遍。
 *
 * <h3>⚠️ 什么时候<b>不要</b>用它</h3>
 * 措辞不同就说明业务语义不同，desc 是要发给前端展示的，不能强行统一：
 * <ul>
 *   <li>{@code t_lottery_config.status} 是 0-下线 / 1-上线</li>
 *   <li>{@code t_prize_pool_config.status} 是 0-关闭 / 1-开启</li>
 *   <li>{@code t_mall_commodity.status} 有三个值（下架 / 上架 / 草稿）</li>
 * </ul>
 * 这些各自建枚举，不要为了"复用"把它们塞进来。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum EnableStatusEnum implements BaseEnum {

    DISABLED(0, "停用"),

    ENABLED(1, "启用"),
    ;

    private final Integer value;

    private final String desc;
}
