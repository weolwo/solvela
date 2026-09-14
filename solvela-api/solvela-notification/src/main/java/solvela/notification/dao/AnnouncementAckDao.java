package solvela.notification.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.notification.AnnouncementAck;

import java.time.LocalDateTime;

/**
 * 强制确认公告的确认记录 Dao。
 *
 * <p>🔴 <b>这里没有「查谁没确认」的方法，是刻意的</b>：那是个反连接
 * （目标人群 ➖ 已确认），人群一大就很贵，而要让它快就得物化人群名单 ——
 * 那正是整个设计要避免的 N×M。业务上也不需要：强制确认公告本来就是
 * 「没确认就每次进来都弹」，自带催办。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Mapper
public interface AnnouncementAckDao extends BaseMapper<AnnouncementAck> {

    /**
     * 记一次确认。
     *
     * <p>{@code INSERT IGNORE}：用户在弹窗上连点两下、或者两个端同时确认，
     * 第二次被主键挡住就好，不该报错 —— 那是一次正常的重复点击，不是异常。
     *
     * @return 1 表示这是第一次确认，0 表示之前已经确认过
     */
    int ack(@Param("announcementId") Long announcementId,
            @Param("memberId") Long memberId,
            @Param("ackTime") LocalDateTime ackTime,
            @Param("ackIp") String ackIp);

    /** 某条公告的确认人数。运营看覆盖率用 —— 按 announcement_id 聚合，走主键前缀 */
    long countByAnnouncement(@Param("announcementId") Long announcementId);

    /**
     * 删掉某条公告的全部确认记录。
     *
     * <p>公告归档时先调它，否则 ack 表里会留下一堆指向不存在公告的孤儿行 ——
     * 那些行永远不会被查到，只是白占空间，且会让「确认人数」这类统计对不上。
     */
    int deleteByAnnouncement(@Param("announcementId") Long announcementId);
}
