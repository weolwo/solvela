package solvela.member.risk.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 只做一件事：把 {@code t_member.last_active_time} 刷成现在。
 *
 * <h3>为什么单独一个 Dao</h3>
 * 它是<b>高频</b>的（每个会员每小时一次），而且只写一列。塞进 {@code MemberAuthDao}
 * 会让一个「认证用的查询集合」里混进一个与认证无关的写操作。
 *
 * @Date 2026-10-15
 */
@Mapper
public interface MemberActivityDao {

    /**
     * 刷新活跃时间。
     *
     * <p>🔴 用 {@code NOW()} 而不是把时间作为参数传进来：这一列要和 {@code create_time}
     * 这些库里生成的时间可比（都按同一个会话时区），应用侧再算一遍只会多一个时区出错的机会
     * （交接文档铁律 10）。
     *
     * <p>🔴 {@code update_time = update_time} 不是废话：那一列带 {@code ON UPDATE CURRENT_TIMESTAMP}，
     * 不显式赋值的话每次刷活跃都会把它推到现在 —— 而它是审计字段，意思是「这个会员被改过」。
     * 一个每小时自动跳一次的审计时间等于没有审计时间。MySQL 的规则是<b>显式赋了值就不再自动更新</b>，
     * 赋回它自己正好满足这一点。
     */
    @Update("""
            UPDATE t_member
            SET last_active_time = NOW(), update_time = update_time
            WHERE member_id = #{memberId}
            """)
    int touchActive(@Param("memberId") Long memberId);
}
