package sa.draw.runtime;

/**
 * 抽奖运行态 Redis Key 统一定义
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public final class DrawCacheKey {

    private DrawCacheKey() {
    }

    private static final String PREFIX = "draw:";

    /**
     * 奖项剩余库存：值为剩余数量，-1 表示不限量
     */
    public static String stock(String activityCode, long prizeItemId) {
        return PREFIX + "stock:" + activityCode + ":" + prizeItemId;
    }

    /**
     * 单人已领次数（按奖项 + 周期）。
     *
     * <p>{@code periodBucket} 由 {@link DrawPeriodResolver} 按奖池的 reset_period 算出：
     * 每天是 {@code 20260816}、每周是 {@code 2026W33}、每月是 {@code 202608}，
     * 不重置则是 {@code ALL}。换周期即换 key，计数天然归零 ——
     * 不需要任何定时任务去清理，也不存在「清到一半」的中间态。
     *
     * <p>⚠️ 周期段插在会员名<b>之前</b>：运维按活动清理计数的扫描模式
     * {@code draw:user:{活动}:*} 因此仍然有效（见「抽奖模块-联调造数.sql」的清场说明）。
     */
    public static String userCount(String activityCode, long prizeItemId, String periodBucket, long memberId) {
        return PREFIX + "user:" + activityCode + ":" + prizeItemId + ":" + periodBucket + ":" + memberId;
    }

    /**
     * 抽奖请求幂等标记
     */
    public static String request(String requestId) {
        return PREFIX + "req:" + requestId;
    }

    /**
     * 单用户防刷限流器（按活动）
     */
    public static String rateLimit(String activityCode, long memberId) {
        return PREFIX + "rate:" + activityCode + ":" + memberId;
    }
}
