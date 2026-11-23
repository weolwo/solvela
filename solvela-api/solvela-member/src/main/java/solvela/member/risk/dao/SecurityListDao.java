package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import solvela.member.SecurityList;

/**
 * 安全名单
 *
 * @Date 2026-10-15
 */
@Mapper
public interface SecurityListDao extends BaseMapper<SecurityList> {

    /**
     * 后台撤销：有效 → 已撤销，记下撤销人、原因、时间（数据库时钟）。
     *
     * <p>🔴 「仍是有效」压在 WHERE 里：连点两次、或与黑到期转灰撞车时，第二次影响 0 行，
     * 不会把第一次记的撤销人覆盖掉。以前是先读后 updateById，只在顺序执行时成立。
     * 状态值写成 ${@...@X.value}，{@code SecurityListStatusSqlTest} 渲染 SQL 兜住常量改名。
     *
     * @return 0 表示这一行不存在或已经不是有效
     */
    @Update("""
            UPDATE t_security_list
               SET status = ${@solvela.enums.SecurityListStatusEnum@REVOKED.value},
                   revoke_operator = #{operator}, revoke_reason = #{reason}, revoke_time = NOW()
             WHERE id = #{id} AND status = ${@solvela.enums.SecurityListStatusEnum@VALID.value}
            """)
    int revokeValid(@Param("id") Long id, @Param("operator") String operator, @Param("reason") String reason);
}
