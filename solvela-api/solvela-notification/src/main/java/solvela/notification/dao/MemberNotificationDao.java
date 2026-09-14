package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.MemberNotification;
import solvela.notification.domain.dto.MemberNotificationDTO;
import solvela.notification.domain.query.MemberNotificationQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员通知 Dao。
 *
 * <p>🔴 <b>所有按会员的查询都必须能吃到 {@code idx_member_read(member_id, read_flag, id)}。</b>
 * 这张表会长到亿级，一次走不上索引的查询就是一次全表扫。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface MemberNotificationDao extends BaseMapper<MemberNotification> {

    /**
     * 收件箱列表（通知 tab）。按 id 倒序 —— 自增 id 与时间同序，
     * 所以不需要按 create_time 排，直接吃索引末位的 id。
     */
    List<MemberNotificationDTO> queryPage(Page<?> page, @Param("queryForm") MemberNotificationQuery queryForm);

    /**
     * 未读数。前端红点用。
     *
     * <p>走 {@code idx_member_read} 的前两列，是个覆盖索引上的 count，很便宜。
     */
    long countUnread(@Param("memberId") Long memberId);

    /**
     * 把一条标记为已读。带 {@code member_id} 条件是<b>越权保护</b>：
     * 只有 id 的话，改一个数字就能把别人的通知标成已读。
     *
     * @return 影响行数。0 表示这条不存在、或不属于这个会员，调用方据此决定是否报错
     */
    int markRead(@Param("id") Long id,
                 @Param("memberId") Long memberId,
                 @Param("now") LocalDateTime now);

    /**
     * 全部已读：一次 UPDATE，不是 N 次。
     *
     * <p>条件里带 {@code read_flag = 0}，所以重复点第二次影响行数就是 0，天然幂等。
     */
    int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    /**
     * 归档用：删掉 {@code create_time} 早于 {@code before} 的，一次最多 {@code limit} 行。
     *
     * <p>走 {@code idx_create_time}。<b>分批不是性能优化</b> —— 一次删几十万行会长时间
     * 持有行锁、撑爆 binlog，一个清理任务不该有能力影响线上业务。
     *
     * @return 实际删除行数
     */
    int deleteExpiredBatch(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 归档 job 的 dryRun 用：数一下有多少条待清理，不动数据。
     */
    long countExpired(@Param("before") LocalDateTime before);
}
