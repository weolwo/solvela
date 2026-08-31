package solvela.activity.runtime;

import solvela.exception.BusinessException;
import solvela.marketing.api.ActivityPlayKeys;
import solvela.scriptengine.spi.EngineContext;

/**
 * {@code ACTIVITY_PLAY} 场景的<b>内部数据通道</b>，类型化。
 *
 * <h3>它解决什么</h3>
 * 这三个值走内部通道而不是脚本变量，是为了让脚本改不掉它们
 * （脚本里写 {@code memberId = 10086} 只会改脚本自己那份副本）。
 * 代价是每个用到它们的 Java 函数都要写一遍：
 * <pre>
 *   Long memberId = context.getInternal(ActivityPlayKeys.MEMBER_ID, Long.class);
 *   String activityCode = context.getInternal(ActivityPlayKeys.ACTIVITY_CODE, String.class);
 *   if (memberId == null || activityCode == null) { throw ... }
 * </pre>
 * 字符串键、无类型、每处各校验一遍 —— 抄第二遍的时候就已经不一致了
 * （抽奖函数校验两个值、次数函数也校验两个值，但报错文案各写各的）。
 *
 * <h3>🔴 现在这里是内部通道的唯一出入口</h3>
 * 绑定走 {@link #bindInto}，读取走 {@link #of}，别处不要再直接碰
 * {@link ActivityPlayKeys}。通道里放了什么、少了什么算错误，只有这一个地方知道 ——
 * 加一个字段时不会漏改某个函数。
 *
 * <h3>为什么校验放在读取侧而不是绑定侧</h3>
 * 绑定侧（活动域门面）是可信的，它一定会绑全；真正会出事的是<b>有人在别的场景调了这些函数</b>
 * —— 那时通道里根本没这些键。所以在读的时候拦，报错才说得清「本函数只能在 ACTIVITY_PLAY 里调」。
 *
 * @param memberId     会员号。<b>权威值</b>，与脚本变量里那份同名副本无关
 * @param activityCode 活动编码
 * @param requestId    幂等键，可为空（调用方没传就等于不启用幂等）
 */
public record ActivityPlayContext(Long memberId, String activityCode, String requestId) {

    /**
     * 把身份绑进内部通道。由活动域门面在进入脚本前调用。
     */
    public EngineContext bindInto(EngineContext context) {
        return context
                .bindInternal(ActivityPlayKeys.MEMBER_ID, memberId)
                .bindInternal(ActivityPlayKeys.ACTIVITY_CODE, activityCode)
                .bindInternal(ActivityPlayKeys.REQUEST_ID, requestId);
    }

    /**
     * 从内部通道取出身份。
     *
     * <p>拿不到就抛：这是<b>编程错误</b>（函数被用在没绑上下文的场景里），不是业务失败。
     * 🔴 绝不能兜底成默认值 —— 抽奖兜底成匿名、次数兜底成 0，
     * 都会变成「静默地把限制废掉」，而且不会有任何报错。
     *
     * @param functionName 正在调用的脚本函数名，进报错信息，让人一眼知道是哪个函数用错了地方。
     *                     由引擎传入，调用方不需要自己记
     */
    public static ActivityPlayContext of(EngineContext context, String functionName) {
        Long memberId = context.getInternal(ActivityPlayKeys.MEMBER_ID, Long.class);
        String activityCode = context.getInternal(ActivityPlayKeys.ACTIVITY_CODE, String.class);
        if (memberId == null || activityCode == null) {
            throw new BusinessException("脚本函数 [" + functionName + "] 缺少执行上下文（memberId / activityCode）。"
                    + "这类函数只能在 ACTIVITY_PLAY 场景里调用，其它场景没有绑定这些内部数据。");
        }
        // requestId 允许为空：没传等于这次调用不启用幂等，是合法的
        return new ActivityPlayContext(memberId, activityCode,
                context.getInternal(ActivityPlayKeys.REQUEST_ID, String.class));
    }
}
