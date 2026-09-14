package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.Announcement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告 Dao。
 *
 * <p>🔴 所有面向 C 端的查询都必须带上三个条件：{@code status = 1}、
 * {@code publish_time <= now}、{@code expire_time > now}。少一个的表现分别是
 * 「下架的公告还在显示」「预发布的提前泄露」「过期的永远占着红点」。
 * 这三条写在 {@code visible_conditions} 这个 SQL 片段里，各处 include 它，别各写一遍。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface AnnouncementDao extends BaseMapper<Announcement> {

    /**
     * 公告 tab 的列表：当前可见 + 命中人群，按 id 倒序。
     *
     * <p>人群判定放在 SQL 里而不是捞出来在 Java 里过滤 —— 后者会让分页彻底失效
     * （数据库返回 20 条、过滤剩 3 条，页码就对不上了）。
     *
     * @param memberCreateTime 会员注册时间，判 REGISTER_BEFORE / REGISTER_AFTER 用
     */
    List<Announcement> selectVisible(@Param("now") LocalDateTime now,
                                     @Param("memberCreateTime") LocalDateTime memberCreateTime,
                                     @Param("limit") int limit,
                                     @Param("lastId") Long lastId);

    /**
     * 公告 tab 的未读数：可见 + 命中人群 + id 大于游标。
     *
     * <p>🔴 <b>这个数与公告总数相关，与用户数无关</b>，所以它随公告增长而变慢，
     * 而不是随用户增长。公告一年几百条，这个 count 永远是小数量级。
     */
    long countUnread(@Param("now") LocalDateTime now,
                     @Param("memberCreateTime") LocalDateTime memberCreateTime,
                     @Param("lastReadId") Long lastReadId);

    /** 当前可见公告里的最大 id。「全部已读」就是把游标推到它 */
    Long selectMaxVisibleId(@Param("now") LocalDateTime now);

    /**
     * 当前对这个会员生效、且<b>还没确认过</b>的强制确认公告。
     *
     * <p>进 App 时查一次，有就弹窗。用 LEFT JOIN + IS NULL 而不是 NOT IN 子查询：
     * 强制确认公告一年就几条，join 的右表也极小。
     */
    List<Announcement> selectPendingAck(@Param("now") LocalDateTime now,
                                        @Param("memberId") Long memberId,
                                        @Param("memberCreateTime") LocalDateTime memberCreateTime);

    /**
     * 归档用：删掉已经过期超过保留期的公告。
     *
     * <p>公告表一年几百行，本来不需要归档 —— 加这个是为了让
     * {@code expire_time} 的三个用途都落到实处，也顺手把 ack 的孤儿行问题挡住
     * （删公告前要先删它的 ack，见 {@code AnnouncementAckDao#deleteByAnnouncement}）。
     */
    int deleteExpiredBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
