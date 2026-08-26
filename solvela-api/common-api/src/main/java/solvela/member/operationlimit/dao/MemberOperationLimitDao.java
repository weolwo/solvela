package solvela.member.operationlimit.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import solvela.member.operationlimit.domain.entity.MemberOperationLimit;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员操作限制 Dao
 *
 * @Date 2026-08-26
 */
@Mapper
public interface MemberOperationLimitDao extends BaseMapper<MemberOperationLimit> {

    /**
     * 查「此刻生效中」的限制：状态未解冻 <b>且</b> 未到期。
     *
     * <p>两个条件缺一不可：只看 status 会把已过期但没回写的行当成还锁着（用户白等）；
     * 只看 expire_time 会把客服刚解掉的行当成还锁着（客服白解）。
     *
     * <p>取最新一行而不是 limit 1 无序 —— 同一 (会员, 操作) 理论上不该有两条生效中的行，
     * 但真出现了（并发触发），按最晚那条算才是安全的一侧。
     */
    @Select("""
            SELECT * FROM t_member_operation_limit
            WHERE member_id = #{memberId} AND operation_type = #{operationType}
              AND status = 0 AND expire_time > #{now}
            ORDER BY expire_time DESC LIMIT 1
            """)
    MemberOperationLimit selectActive(@Param("memberId") Long memberId,
                                      @Param("operationType") Integer operationType,
                                      @Param("now") LocalDateTime now);

    /**
     * 解冻：把仍处于「冻结中」的行推到终态。
     *
     * <p>{@code status = 0} 这个条件是幂等保护 —— 客服连点两次、或人工解冻与到期回写撞车时，
     * 第二次影响 0 行，不会把第一次记的 unlock_type / operator 覆盖掉。
     *
     * @return 实际更新的行数，0 表示没有生效中的限制
     */
    @Update("""
            UPDATE t_member_operation_limit
            SET status = 1, unlock_time = #{unlockTime}, unlock_type = #{unlockType},
                `operator` = #{unlockOperator}, remark = #{remark}, update_time = #{unlockTime}
            WHERE member_id = #{memberId} AND operation_type = #{operationType} AND status = 0
            """)
    int unlock(@Param("memberId") Long memberId,
               @Param("operationType") Integer operationType,
               @Param("unlockTime") LocalDateTime unlockTime,
               @Param("unlockType") Integer unlockType,
               @Param("unlockOperator") String unlockOperator,
               @Param("remark") String remark);

    /**
     * 到期回写：把已过期却还挂着「冻结中」的行批量推到终态，unlock_type = 自动到期。
     *
     * <p>纯粹为了让表的状态列可信（报表、客服列表不用再自己算过期）。
     * 业务判断从不依赖它 —— 见 {@link #selectActive}。
     */
    @Update("""
            UPDATE t_member_operation_limit
            SET status = 1, unlock_time = expire_time, unlock_type = 1, update_time = #{now}
            WHERE status = 0 AND expire_time <= #{now}
            """)
    int settleExpired(@Param("now") LocalDateTime now);

    /**
     * 某会员的限制历史，最近在前。客服查「这人被限过几次」用
     */
    @Select("""
            SELECT * FROM t_member_operation_limit
            WHERE member_id = #{memberId}
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<MemberOperationLimit> selectRecentByMember(@Param("memberId") Long memberId,
                                                    @Param("limit") int limit);
}
