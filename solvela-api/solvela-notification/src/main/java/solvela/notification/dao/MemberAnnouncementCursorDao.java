package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.MemberAnnouncementCursor;

/**
 * 公告已读游标 Dao。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface MemberAnnouncementCursorDao extends BaseMapper<MemberAnnouncementCursor> {

    /**
     * 推进游标（懒创建）。
     *
     * <p>🔴 用 {@code INSERT ... ON DUPLICATE KEY UPDATE} + {@code GREATEST}：
     * <ul>
     *   <li><b>upsert</b> —— 用户第一次读公告时还没有这一行，先查后写会在多端同时
     *       打开时插两行（主键会拦住，但报出来是个吓人的 DuplicateKeyException，
     *       而这只是一次正常并发）；</li>
     *   <li><b>GREATEST</b> —— 游标只能前进不能后退。用户在手机上读到 100、
     *       电脑上那个页面还停在 50，两边都推游标的话，不加 GREATEST
     *       会把已读退回 50，表现是「读过的公告又变未读了」。</li>
     * </ul>
     */
    int advance(@Param("memberId") Long memberId, @Param("lastReadId") Long lastReadId);
}
