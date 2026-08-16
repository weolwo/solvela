package sa.lottery.runtime;

/**
 * 彩票运行态 Redis Key 统一定义
 *
 * @Author alaric
 * @Date 2026-07-28
 */
public final class LotteryCacheKey {

    private LotteryCacheKey() {
    }

    private static final String PREFIX = "lottery:";

    /**
     * 发号游标：1-indexed 自增，值 = 已消耗的槽位数。
     *
     * ⚠️ 这个 key <b>只增不减</b>。回滚它会让两个用户拿到同一个游标、进而拿到同一个号码，
     * 是本模块最严重的故障。浪费一个号（留个空洞）可以接受，发重号不行。
     *
     * ⚠️ 清场时必须与 DB 一起清，且顺序是「先清 DB，再清 Redis」：
     * DB 清空而 Redis 游标留在高位只是浪费号段（安全）；
     * 反过来 Redis 被清而 DB 还有记录，游标会从头重来，直接撞 uk_issue_ticket。
     */
    public static String sequence(String lotteryCode, String issueNo) {
        return PREFIX + "seq:" + lotteryCode + ":" + issueNo;
    }

    /**
     * 冷启动回源的分布式锁：Redis 游标丢失时，保证只有一个线程去 DB 查 MAX(sequence_no) 回填
     */
    public static String sequenceLock(String lotteryCode, String issueNo) {
        return PREFIX + "seq:lock:" + lotteryCode + ":" + issueNo;
    }

    /**
     * 领号请求幂等标记
     */
    public static String request(String requestId) {
        return PREFIX + "req:" + requestId;
    }

    /**
     * 单用户防刷限流器（按彩票玩法）。
     * 注意这是防刷，不是业务限购 —— 限购由上游业务负责，本模块允许同一用户合法领任意多张。
     */
    public static String rateLimit(String lotteryCode, String memberName) {
        return PREFIX + "rate:" + lotteryCode + ":" + memberName;
    }
}
