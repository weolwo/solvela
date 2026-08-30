package solvela.base.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SnowFlake {

    private static SnowflakeIdGenerator snowflake;

    /**
     * 初始化配置
     *
     * @param workerId
     * @param datacenterId
     */
    public static void initialize(long workerId, long datacenterId) {
        snowflake = new SnowflakeIdGenerator(workerId, datacenterId);
    }

    public static long getId() {
        return snowflake.nextId();
    }

    /**
     * 生成字符，带有前缀
     *
     * @param prefix
     * @return
     */
    public static String createStr(String prefix) {
        return prefix + SnowFlake.getId();
    }

    public static String getIdStr() {
        return snowflake.nextId() + "";
    }
}
