package net.lab1024.sa.draw.runtime;

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
     * 单人已领次数（按奖项）
     */
    public static String userCount(String activityCode, long prizeItemId, String memberName) {
        return PREFIX + "user:" + activityCode + ":" + prizeItemId + ":" + memberName;
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
    public static String rateLimit(String activityCode, String memberName) {
        return PREFIX + "rate:" + activityCode + ":" + memberName;
    }
}
