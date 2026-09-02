package solvela.app.domain;

import java.util.List;

/**
 * C 端看到的抽奖结果。连抽就是 {@code records} 有多条，单抽是一条。
 *
 * <h3>刻意<b>不</b>下发的两个字段</h3>
 * <ul>
 *   <li>{@code prizeItemId} —— 奖池内部主键。下发它等于把配置结构暴露给客户端；</li>
 *   <li>{@code source} —— 命中来源（概率/白名单/兜底降级）。这是给排查和风控看的：
 *       让用户知道自己是「白名单命中」的，等于告诉他这个活动内定了。</li>
 * </ul>
 *
 * <p>「没被受理」不在这里 —— 它由 HTTP 状态码表达（4xx），不是一个 body 字段。
 * 能拿到本对象就说明这一批真的抽了。
 *
 * <h3>为什么单抽也是列表</h3>
 * 不给单抽留一个 {@code hit} 顶层字段，是为了让前端<b>只有一条渲染路径</b>。
 * 两种形状意味着前端要写两套解析，而「单抽」不过是 {@code records.length == 1}。
 *
 * @param records  每一次的结果，顺序即抽奖顺序
 * @param hitCount 中了几个。前端自己数也行，但页面上要显示的正是这个数
 * @param message  展示文案
 */
public record DrawView(List<DrawItemView> records, long hitCount, String message) {

    /**
     * 其中一次。
     *
     * @param hit       是否中奖
     * @param prizeCode 中奖奖品编码；未中奖为 null
     */
    public record DrawItemView(boolean hit, String prizeCode) {
    }
}
