package sa.base.common.enumeration;

import lombok.Getter;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Getter
public enum LuaScriptEnum {

    ASSIGN_LOTTERY("assign_Lottery", "彩票分配预扣脚本", new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('get', KEYS[1]))
            local num = tonumber(ARGV[1])
            if (stock == nil or stock < num) then
                return -1 -- 库存不足
            else
              local remain =  redis.call('decrby', KEYS[1], num)
              return remain  -- 扣减成功,返回剩余
            end
            """, Long.class));

    private final String code;
    private final String desc;

    // 存一个 String 类型的 script，那么在业务层每次调用时，都需要 new DefaultRedisScript<>(script)。
    // 在 10000 并发下，这会瞬间创建 10000 个重复的 DefaultRedisScript 对象，不仅增加 GC 压力，每次实例化底层还要重新解析返回值类型。
    // 直接在枚举初始化的那一刻，就把 Spring 的 DefaultRedisScript 对象给创建好。这样全局就只有一份实例，性能拉满。
    private final DefaultRedisScript<Long> redisScript;

    LuaScriptEnum(String code, String desc, DefaultRedisScript<Long> redisScript) {
        this.code = code;
        this.desc = desc;
        this.redisScript = redisScript;
    }
}