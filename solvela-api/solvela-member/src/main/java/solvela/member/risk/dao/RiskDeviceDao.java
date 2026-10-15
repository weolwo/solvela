package solvela.member.risk.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 风控要读的设备事实。<b>只有一条查询</b>：这台设备算不算「老设备」。
 *
 * <h3>为什么不放进 {@code DeviceDao}</h3>
 * 这条 SQL 的主表是 {@code t_device}，但它要连 {@code t_member_login_log} ——
 * 那是两个域的表。放在风控自己的 dao 下，和 {@code MemberActivityDao}（风控写 {@code t_member}
 * 的那一列）同一种做法：<b>谁用谁负责</b>，设备域不必为了风控的一个判据多一个方法。
 *
 * @Date 2026-10-16
 */
@Mapper
public interface RiskDeviceDao {

    /**
     * 这台设备是不是「老设备」：令牌签发早于 {@code minAgeSeconds}，<b>并且</b>这台机器上有过成功登录。
     *
     * <h3>🔴 两个条件缺一不可</h3>
     * 设备令牌是<b>匿名领的</b>（{@code /device/register} 不要求登录）：
     * <ul>
     *   <li>只看年龄 —— 攻击者提前领一批令牌养七天，就有了一把免检通行证；</li>
     *   <li>只看有过成功登录 —— 刚领的令牌登一次就免检，而撞库脚本中一个号就能拿到它。</li>
     * </ul>
     *
     * <h3>🔴 不带 member_id：判的是【设备】的年龄，不是某个账号与设备的关系</h3>
     * 后者要先知道是谁，而这条判断发生在<b>前置</b>、可能查无此人 ——
     * 依赖账号就意味着「有号」和「没号」在 IP 规则上的行为不一样，那是一个账号枚举口子。
     *
     * <h3>时间比较全部在 SQL 里做</h3>
     * {@code create_time} 是数据库按会话时区写的。从 Java 传一个 LocalDateTime 进来的话，
     * JVM 时区与会话时区一旦不一致，门槛就会静默偏移几个小时 ——
     * 同 {@code MemberLoginLogDao.existsTrustedLogin} 的写法。
     *
     * @param minAgeSeconds 设备年龄门槛，秒
     * @param successStatus {@code LoginLogResultEnum.LOGIN_SUCCESS} 的值
     */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM t_device d
               WHERE d.device_id = #{deviceId}
                 AND d.create_time <= NOW() - INTERVAL #{minAgeSeconds} SECOND
                 AND EXISTS(
                   SELECT 1 FROM t_member_login_log l
                    WHERE l.device_id = d.device_id AND l.status = #{successStatus}
                 )
            )
            """)
    boolean isOldDevice(@Param("deviceId") String deviceId,
                        @Param("minAgeSeconds") long minAgeSeconds,
                        @Param("successStatus") int successStatus);
}
