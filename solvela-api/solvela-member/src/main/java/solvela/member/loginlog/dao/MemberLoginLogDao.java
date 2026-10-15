package solvela.member.loginlog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.loginlog.domain.query.MemberLoginLogQuery;
import solvela.member.MemberLoginLog;
import solvela.member.loginlog.domain.dto.MemberLoginLogStatDTO;
import solvela.member.loginlog.domain.dto.MemberLoginLogDTO;

import java.util.List;

/**
 * 会员登录日志（append-only，按月分区） Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */
@Mapper
public interface MemberLoginLogDao extends BaseMapper<MemberLoginLog> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberLoginLogDTO> queryPage(Page<?> page, @Param("queryForm") MemberLoginLogQuery queryForm);

    /**
     * 统计：一趟 SQL 出全部指标，条件与列表复用同一段 query_conditions
     */
    MemberLoginLogStatDTO queryStat(@Param("queryForm") MemberLoginLogQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberLoginLogDTO> queryList(@Param("queryForm") MemberLoginLogQuery queryForm);

    /**
     * 画像用：该会员近 N 天的成功登录，<b>按时间倒序</b>，只取 IP 与时间。
     *
     * <h3>为什么不取 {@code ip_region}</h3>
     * 那一列是压缩过的展示串（缺失的段被删掉了），第二项是省还是运营商分不出来。
     * 判断要按位置取字段，所以这里只拿 {@code client_ip} 回去重新解析（见 {@code IpIntel}）。
     *
     * <h3>为什么限条数</h3>
     * 「常用网段」只要最近那几个就够了，而一个被撞库刷过的账号可能有成百上千条记录。
     * 不限条数的话，这个查询会随着被攻击的程度变慢 —— 而它在登录路径上。
     *
     * <p>走 {@code idx_mbr_log_member}。时间比较在 SQL 里做，理由同 {@link #existsTrustedLogin}。
     *
     * @param lookbackDays 回看多少天
     * @param limit        最多取多少条
     */
    @Select("""
            SELECT client_ip, create_time
              FROM t_member_login_log
             WHERE member_id = #{memberId}
               AND status = #{successStatus}
               AND client_ip IS NOT NULL
               AND create_time >= NOW() - INTERVAL #{lookbackDays} DAY
             ORDER BY create_time DESC
             LIMIT #{limit}
            """)
    List<solvela.member.loginlog.domain.dto.LoginTraceDTO> selectRecentSuccess(
            @Param("memberId") Long memberId,
            @Param("successStatus") int successStatus,
            @Param("lookbackDays") int lookbackDays,
            @Param("limit") int limit);

    /**
     * 该会员有没有过<b>任何</b>一次成功登录。新设备提醒用：一次都没有 = 注册后的第一次登录，不该提醒。
     */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM t_member_login_log WHERE member_id = #{memberId} AND status = #{successStatus}
            )
            """)
    boolean existsSuccessfulLogin(@Param("memberId") Long memberId, @Param("successStatus") int successStatus);

    /**
     * 该会员在<b>这台设备</b>上有没有过成功登录。走 {@code idx_mbr_log_device}。
     */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM t_member_login_log
               WHERE device_id = #{deviceId} AND member_id = #{memberId} AND status = #{successStatus}
            )
            """)
    boolean existsSuccessfulLoginOnDevice(@Param("memberId") Long memberId,
                                          @Param("deviceId") String deviceId,
                                          @Param("successStatus") int successStatus);

    /**
     * 这台设备上，该会员有没有一次<b>足够早</b>的成功登录 —— 设备信任的「老交情」那一档。
     *
     * <p>条件是「存在一次成功登录，落在 [起算点, 现在 - 门槛] 之间」，
     * 等价于「起算点之后的首次成功登录早于门槛」，但不需要取 MIN 再比。
     *
     * <h3>🔴 时间比较全部在 SQL 里做</h3>
     * {@code create_time} 是数据库按<b>会话时区</b>写的（交接文档铁律 10）。
     * 「现在」用 {@code NOW()}、起算点用 {@code FROM_UNIXTIME}，两边落在同一个会话时区里；
     * 从 Java 传一个 LocalDateTime 进来的话，JVM 时区与会话时区一旦不一致，门槛就会静默偏移几个小时。
     *
     * <p>走 {@code idx_mbr_log_device (device_id, create_time)}：一台设备的登录记录天然很少，
     * member_id 在索引命中之后再过滤。
     *
     * @param sinceEpochSeconds 信任起算点（Unix 秒），0 表示不限制
     * @param trustAfterSeconds 门槛，秒
     * @param successStatus     {@code LoginLogResultEnum.LOGIN_SUCCESS} 的值
     */
    @Select("""
            SELECT EXISTS(
              SELECT 1 FROM t_member_login_log
               WHERE device_id = #{deviceId}
                 AND member_id = #{memberId}
                 AND status = #{successStatus}
                 AND create_time >= FROM_UNIXTIME(#{sinceEpochSeconds})
                 AND create_time <= NOW() - INTERVAL #{trustAfterSeconds} SECOND
            )
            """)
    boolean existsTrustedLogin(@Param("memberId") Long memberId,
                               @Param("deviceId") String deviceId,
                               @Param("sinceEpochSeconds") long sinceEpochSeconds,
                               @Param("trustAfterSeconds") long trustAfterSeconds,
                               @Param("successStatus") int successStatus);
}
