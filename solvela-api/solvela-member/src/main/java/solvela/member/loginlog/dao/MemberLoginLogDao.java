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
