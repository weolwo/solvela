package solvela.base.module.redis;

import jakarta.annotation.Resource;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.base.util.SolvelaStringUtil;
import solvela.base.constant.RedisKeyConst;
import solvela.base.json.JsonUtils;
import org.slf4j.Logger;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * redis 一顿操作
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/25 21:57
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class RedisService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RedisService.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ValueOperations<String, String> redisValueOperations;

    @Resource
    private HashOperations<String, String, Object> redisHashOperations;

    @Resource
    private ListOperations<String, Object> redisListOperations;

    @Resource
    private SetOperations<String, Object> redisSetOperations;

    @Resource
    private SystemEnvironment systemEnvironment;


    /**
     * 生成redis key
     * @param prefix
     * @param key
     * @return
     */
    public String generateRedisKey(String prefix, String key) {
        SystemEnvironmentEnum currentEnvironment = systemEnvironment.getCurrentEnvironment();
        return systemEnvironment.getProjectName() + RedisKeyConst.SEPARATOR + currentEnvironment.getValue() +  RedisKeyConst.SEPARATOR + prefix + key;
    }

    /**
     * redis key 解析成真实的内容
     * @param redisKey
     * @return
     */
    public static String redisKeyParse(String redisKey) {
        if (SolvelaStringUtil.isBlank(redisKey)) {
            return "";
        }
        int index = redisKey.lastIndexOf(RedisKeyConst.SEPARATOR);
        if(index < 1){
            return redisKey;
        }
        return redisKey.substring(index);
    }

    public boolean getLock(String key, long expire) {
        return redisValueOperations.setIfAbsent(key, String.valueOf(System.currentTimeMillis()), expire, TimeUnit.MILLISECONDS);
    }

    public void unLock(String key) {
        redisValueOperations.getOperations().delete(key);
    }

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒)
     * @return
     */
    public boolean expire(String key, long time) {
        return redisTemplate.expire(key, time, TimeUnit.SECONDS);
    }

    /**
     * 计数 +1，并<b>只在第一次</b>计数时打上过期时间，返回累计值。
     *
     * <p>「只在第一次」是关键：每次都 expire 会让 TTL 被不断续期，窗口永远不结束 ——
     * 一个每 29 分钟试一次密码的脚本能把计数一直累加到锁定，而它其实从没在任一 30 分钟窗口里
     * 超过阈值。这里的语义是<b>固定窗口</b>：第一次失败起算，TTL 到点整个计数清零。
     *
     * <p>🔴 <b>INCR 与 EXPIRE 必须在同一个脚本里</b>，不能写成两条命令。
     * INCR 自己是原子的，但「INCR 完再 EXPIRE」这一对不是：两条命令之间应用被 kill、
     * 连接断开、或 Redis 主从切换，都会留下一个<b>没有 TTL 的计数键</b>。
     * 那个键此后永不过期，计数只增不减 —— 表现是这个会员/这个活动的限流<b>再也不会解除</b>，
     * 而且查不出原因：代码看着没问题，数据库里也没有任何限制记录。
     *
     * <p>脚本里还多了一层保险：即使键已存在却没有 TTL（历史遗留的坏键），
     * 也会补上过期时间，让老数据自己愈合。
     *
     * @param expireSeconds 窗口长度（秒）
     */
    public long increment(String key, long expireSeconds) {
        Long count = stringRedisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(expireSeconds));
        return count == null ? 0L : count;
    }

    /**
     * INCR + 首次设置 TTL，一次往返、原子完成。
     *
     * <p>{@code PTTL} 返回 -1 表示键存在但没有过期时间，-2 表示键不存在。
     * 新建（返回值 1）或发现没有 TTL 时都补上 —— 后者是为了修复历史上由非原子写法留下的坏键。
     */
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 or redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """, Long.class);

    /**
     * 获取当天剩余的秒数
     *
     * @return
     */
    public static long currentDaySecond() {
        return ChronoUnit.SECONDS.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now(), LocalTime.MAX));
    }

    /**
     * 根据key 获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    public void delete(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete(Arrays.asList(key));
            }
        }
    }

    /**
     * 删除缓存
     *
     * @param keyList
     */
    public void delete(List<String> keyList) {
        if (CollectionUtils.isEmpty(keyList)) {
            return;
        }
        redisTemplate.delete(keyList);
    }

    //============================String=============================

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public String get(String key) {
        return key == null ? null : redisValueOperations.get(key);
    }

    public <T> T getObject(String key, Class<T> clazz) {
        Object json = this.get(key);
        if (json == null) {
            return null;
        }
        T obj = JsonUtils.parseObject(json.toString(), clazz);
        return obj;
    }


    /**
     * 普通缓存放入
     */
    public void set(String key, String value) {
        redisValueOperations.set(key, value);
    }
    public void set(Object key, Object value) {
        String jsonString = JsonUtils.toJson(value);
        redisValueOperations.set(key.toString(), jsonString);
    }

    /**
     * 普通缓存放入
     */
    public void set(String key, String value, long second) {
        redisValueOperations.set(key, value, second, TimeUnit.SECONDS);
    }

    /**
     * 普通缓存放入并设置时间
     */
    public void set(Object key, Object value, long second) {
        String jsonString = JsonUtils.toJson(value);
        if (second > 0) {
            redisValueOperations.set(key.toString(), jsonString, second, TimeUnit.SECONDS);
        } else {
            set(key.toString(), jsonString);
        }
    }

    //============================ map =============================


    public void mset(String key, String hashKey, Object value) {
        redisHashOperations.put(key, hashKey, value);
    }

    public Object mget(String key, String hashKey) {
        return redisHashOperations.get(key, hashKey);
    }

}