package solvela.member.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.DeviceStatusEnum;
import solvela.member.Device;
import solvela.member.device.dao.DeviceDao;

import solvela.security.SecurityKeys;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 设备的<b>处置闭环</b>：活跃刷新、自动降档、自动回档、人工封禁。
 *
 * <h3>在它之前，t_device.status 是一列死数据</h3>
 * 签发时写 0，此后<b>全仓没有一处读它、没有一处改它</b>。
 * 三档（正常/观察/封禁）、{@code idx_dev_status} 索引、{@code remark} 与
 * {@code operator} 两列，全都是为一件从没发生过的事准备的。本类让它们开始动。
 *
 * <h3>🔴 Redis 是计时器，MySQL 是账本 —— 分工不能反</h3>
 * 「这台设备现在还在观察期吗」由 Redis 里一个带 TTL 的键回答：这个判断在登录路径上，
 * 每次都查库太贵；而且「到期自动回档」用 TTL 表达最自然，不需要定时任务。
 *
 * <p>{@code t_device.status} 是<b>可读的影子</b>：后台要看得见、客服要查得到、
 * 将来要按它统计。两者不一致时<b>以 Redis 为准</b>，影子在下一次读到这台设备时被纠正。
 *
 * <p>⚠️ 已知的不一致窗口：一台设备被降到观察档之后<b>再也没来过</b>，
 * Redis 键过期了，而库里那行还写着 1。后台看到「观察中」，实际早已不在观察。
 * 这个偏差只影响展示，不影响拦截 —— 拦不拦看 Redis。要彻底消掉得加一个定时任务
 * 扫 {@code idx_dev_status}，那是「后台开始按这一列做统计」之后才值得做的事。
 *
 * <h3>封禁只能人工，库是真相源，Redis 里只有一份镜像</h3>
 * {@link DeviceStatusEnum#BANNED} 由库里那一行说了算，没有 TTL、不会自动解除。
 * 自动封禁一旦误判就是「这台机器再也用不了」，而受害者说不清自己遇到了什么 ——
 * 那种代价必须有人签字。
 *
 * <p>执行在网关：安全闸查 {@link SecurityKeys#BANNED_DEVICES} 这个集合，被封的设备访问<b>任何</b>接口都在网关结束
 * （网关不连库）。本类在人工处置时同步这份镜像，{@link #rebuildBannedMirror} 按库全量重建 —— 镜像丢了、
 * 同步失败了，由定时任务补回来（{@code DeviceBanMirrorRebuildJob}）。
 *
 * @Date 2026-09-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDispositionService {

    /** 活跃刷新的节流键。存在即表示「这个窗口内已经写过库了」。 */
    private static final String KEY_ACTIVE = "dev:active:";

    /** 观察期键。它的<b>存在</b>就是「在观察中」，它的 TTL 就是「还要观察多久」。 */
    private static final String KEY_OBSERVE = "dev:observe:";

    /** remark 列宽 128。 */
    private static final int REMARK_MAX = 128;

    private final DeviceDao deviceDao;

    private final RedisService redisService;

    private final StringRedisTemplate redis;

    private final DeviceProperties properties;

    // ------------------------------------------------------------------ 活跃刷新

    /**
     * 刷新最后活跃时间。<b>每个请求都会调，所以第一件事是节流</b>。
     *
     * <p>不节流的话，一台活跃设备一天几千次 UPDATE，{@code t_device} 会变成热点表 ——
     * 而这一列的精度到小时就够用（DDL 注释写的就是「节流写（&gt;1h 才更新）」）。
     *
     * <p>🔴 <b>任何异常都吞掉</b>。这是一条纯粹的记录，它失败不该让用户的请求失败 ——
     * 「因为写不了活跃时间所以打不开 App」是荒谬的。
     */
    public void touch(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        try {
            Duration throttle = properties.activeThrottle();
            Boolean won = redis.opsForValue()
                    .setIfAbsent(redisService.generateRedisKey(KEY_ACTIVE, deviceId), "1", throttle);
            if (!Boolean.TRUE.equals(won)) {
                /*
                 * 这个窗口内已经有人写过了。
                 * 🔴 null（Redis 异常）也走这里 —— 拿不准的时候不写。
                 * 宁可少一次刷新，也不要在 Redis 挂掉时把这张表打满。
                 */
                return;
            }
            if (deviceDao.touchActive(deviceId) == 0) {
                // 令牌验签通过，库里却没有这一行。只可能是记录被删了而令牌还在用户手上，
                // 或者有人拿到了签名密钥。两种都值得看一眼
                log.warn("【设备活跃】令牌合法但库里查无此设备, deviceId: {}", deviceId);
            }
        } catch (Exception e) {
            log.warn("【设备活跃】刷新失败，已忽略, deviceId: {}", deviceId, e);
        }
    }

    // ------------------------------------------------------------------ 观察档

    /**
     * 把设备推进观察档。命中限流规则时调。
     *
     * <p><b>幂等</b>：已经在观察中的设备再次命中，只把观察期<b>续上</b>，不重复写库。
     * 续期是对的 —— 又犯一次，观察期理应从现在重新算。
     */
    public void observe(String deviceId, String reason) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        try {
            /*
             * 🔴 顺序是【先库后 Redis】，而且不能反过来。
             *
             * 反过来写（先占 Redis 键，再有条件地更新库）会开一个洞：
             * 库里的条件更新挡住了「把封禁改成观察档」，但 Redis 键<b>已经建好了</b> ——
             * 而 currentStatus 是 Redis 优先的，于是一台被人工封禁的设备，
             * 只要之后再命中一次限流规则，就会被判成观察档，<b>封禁当场失效</b>。
             *
             * 让库先说话：改到了才建计时器。这条有用例钉着（自动降档不覆盖封禁）。
             */
            int changed = deviceDao.transitStatus(deviceId,
                    DeviceStatusEnum.NORMAL.getValue(), DeviceStatusEnum.OBSERVE.getValue(),
                    truncate(reason), null);
            String key = redisService.generateRedisKey(KEY_OBSERVE, deviceId);
            if (changed > 0) {
                redis.opsForValue().set(key, String.valueOf(reason), properties.observeWindow());
                log.warn("【设备处置】进入观察档, deviceId: {}, 原因: {}, 观察期: {} 小时",
                        deviceId, reason, properties.observeWindow().toHours());
                return;
            }

            // 改不动，说明当前不是正常档。是已经在观察中，还是已经被封了？
            DeviceStatusEnum stored = storedStatus(deviceId);
            if (stored == DeviceStatusEnum.OBSERVE) {
                // 又犯一次，观察期从现在重新算。用 set 而不是 expire ——
                // Redis 被清过时那个键可能根本不存在，expire 会静默地什么都不做
                redis.opsForValue().set(key, String.valueOf(reason), properties.observeWindow());
                return;
            }
            log.warn("【设备处置】命中规则但不降档, deviceId: {}, 原因: {}, 当前档位: {}",
                    deviceId, reason, stored.getDesc());
        } catch (Exception e) {
            log.warn("【设备处置】进入观察档失败，已忽略, deviceId: {}", deviceId, e);
        }
    }

    /** 库里那一行现在是什么档。查不到当 {@link DeviceStatusEnum#NORMAL}。 */
    private DeviceStatusEnum storedStatus(String deviceId) {
        Device device = deviceDao.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceId, deviceId)
                .select(Device::getStatus));
        return device == null ? DeviceStatusEnum.NORMAL : DeviceStatusEnum.of(device.getStatus());
    }

    /**
     * 这台设备当前的处置档。<b>登录路径上调，所以要便宜</b>。
     *
     * <p>顺序是 <b>Redis → 库</b>，不是反过来：
     * <ul>
     *   <li>Redis 里有观察键 → 观察档。一次 GET，不查库；</li>
     *   <li>没有观察键 → 才看库，因为<b>封禁只存在库里</b>（它不该自动解除）。</li>
     * </ul>
     *
     * <p>拿不到时一律返回 {@link DeviceStatusEnum#NORMAL}：Redis 或库出问题时，
     * 应该是「防刷暂时失效」，不是「全站登不进来」。
     */
    public DeviceStatusEnum currentStatus(String deviceId) {
        if (SolvelaStringUtil.isBlank(deviceId)) {
            return DeviceStatusEnum.NORMAL;
        }
        try {
            if (redisService.get(redisService.generateRedisKey(KEY_OBSERVE, deviceId)) != null) {
                return DeviceStatusEnum.OBSERVE;
            }
            DeviceStatusEnum stored = storedStatus(deviceId);
            if (stored == DeviceStatusEnum.OBSERVE) {
                /*
                 * 库里写着观察档，Redis 那个键却已经过期 —— 观察期满了。
                 * 在这里把影子纠正回来，就不需要定时任务去扫全表：
                 * 「自动回档」因此发生在【这台设备下次出现的时候】，
                 * 而它不再出现的话，回不回档也没有任何影响。
                 */
                int changed = deviceDao.transitStatus(deviceId,
                        DeviceStatusEnum.OBSERVE.getValue(), DeviceStatusEnum.NORMAL.getValue(),
                        null, null);
                if (changed > 0) {
                    log.info("【设备处置】观察期已满，自动回档, deviceId: {}", deviceId);
                }
                return DeviceStatusEnum.NORMAL;
            }
            return stored;
        } catch (Exception e) {
            log.warn("【设备处置】读取处置档失败，按正常放行, deviceId: {}", deviceId, e);
            return DeviceStatusEnum.NORMAL;
        }
    }

    // ------------------------------------------------------------------ 人工处置

    /**
     * 人工封禁 / 解封 / 推进观察档。<b>{@code operator} 必填</b>。
     *
     * <p>不带原值做条件：人工处置就是要能覆盖任何当前状态 —— 客服看到那一行是什么，
     * 他就是要把它改掉。自动降档才需要条件，因为它不该覆盖人的决定。
     *
     * <p>🔴 解封时<b>同时清掉观察键</b>。不清的话，客服在后台看到「已恢复正常」，
     * 而用户下一次登录仍然被要求验证码 —— 那正是「后台说的和实际不一样」，
     * 也是这类功能最常见的失效方式。
     *
     * @return 是否真的改到了一行（false 表示这个 device_id 不存在）
     */
    public boolean disposeManually(String deviceId, DeviceStatusEnum target,
                                   String reason, String operator) {
        if (SolvelaStringUtil.isBlank(deviceId) || target == null
                || SolvelaStringUtil.isBlank(operator)) {
            throw new IllegalArgumentException("人工处置必须带上设备号、目标档位和操作人");
        }
        int changed = deviceDao.update(null, Wrappers.<Device>lambdaUpdate()
                .eq(Device::getDeviceId, deviceId)
                .set(Device::getStatus, target.getValue())
                .set(Device::getRemark, truncate(reason))
                .set(Device::getOperator, operator));

        String observeKey = redisService.generateRedisKey(KEY_OBSERVE, deviceId);
        switch (target) {
            case NORMAL -> redisService.delete(observeKey);
            // 人工推进观察档也要有计时器，否则它永远不会自动回档
            case OBSERVE -> redis.opsForValue().set(observeKey, "manual", properties.observeWindow());
            // 封禁没有计时器：它由库里那一行说了算，不该自动解除
            case BANNED -> redisService.delete(observeKey);
        }
        if (changed > 0) {
            syncBannedMirror(deviceId, target == DeviceStatusEnum.BANNED);
        }

        log.warn("【设备处置】人工处置, deviceId: {}, 目标: {}, 操作人: {}, 原因: {}, 影响行数: {}",
                deviceId, target.getDesc(), operator, reason, changed);
        return changed > 0;
    }

    // ------------------------------------------------------------------ 封禁镜像

    /** 重建时一次 SADD 的设备数 */
    private static final int MIRROR_BATCH = 500;

    /**
     * 人工处置后同步一台设备的镜像。
     *
     * <p>失败只打日志：库已经改了，处置就算成功；镜像最多晚一个重建周期对上。
     * 反过来让「Redis 写不进去」把一次封禁变成报错，客服只会再点一次，而库里那行早就改好了。
     */
    private void syncBannedMirror(String deviceId, boolean banned) {
        try {
            if (banned) {
                redis.opsForSet().add(SecurityKeys.BANNED_DEVICES, deviceId);
            } else {
                redis.opsForSet().remove(SecurityKeys.BANNED_DEVICES, deviceId);
            }
        } catch (Exception e) {
            log.error("【设备处置】封禁镜像同步失败，等定时任务重建, deviceId: {}, 封禁: {}", deviceId, banned, e);
        }
    }

    /**
     * 按库全量重建封禁镜像：先写进一个临时键，再 {@code RENAME} 过去 —— 网关任何时刻读到的都是一份完整的集合，
     * 不会读到「清空了、还没写完」的半成品。库里一台被封的都没有时直接删掉镜像。
     *
     * <p>⚠️ 已知窗口：读库之后、改名之前恰好有人封了一台设备，它的那条 SADD 会被这次改名盖掉，
     * 要等下一个周期才回来。封禁本来就是人工动作、频率很低，这个窗口可以接受。
     *
     * @return 镜像里的设备数
     */
    public int rebuildBannedMirror() {
        List<String> banned = deviceDao.selectObjs(Wrappers.<Device>lambdaQuery()
                        .select(Device::getDeviceId)
                        .eq(Device::getStatus, DeviceStatusEnum.BANNED.getValue()))
                .stream().map(String::valueOf).toList();
        if (banned.isEmpty()) {
            redis.delete(SecurityKeys.BANNED_DEVICES);
            return 0;
        }
        String temp = SecurityKeys.BANNED_DEVICES + ":rebuild:" + UUID.randomUUID();
        try {
            for (int i = 0; i < banned.size(); i += MIRROR_BATCH) {
                redis.opsForSet().add(temp, banned.subList(i, Math.min(i + MIRROR_BATCH, banned.size()))
                        .toArray(String[]::new));
            }
            redis.rename(temp, SecurityKeys.BANNED_DEVICES);
        } finally {
            // 改名成功后临时键已经不在了；中途失败时别把它留在 Redis 里
            redis.delete(temp);
        }
        return banned.size();
    }

    /** 截断而不是让一句过长的原因把整次处置变成 500。 */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= REMARK_MAX ? reason : reason.substring(0, REMARK_MAX);
    }
}
