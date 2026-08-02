package net.lab1024.sa.stat.dao;

import net.lab1024.sa.stat.domain.vo.ParticipationByTypeVO;
import net.lab1024.sa.stat.domain.vo.ParticipationTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销统计 Dao
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Mapper
public interface MarketingStatDao {

    /**
     * 参与趋势：按天 × 玩法类型，统计参与人数
     *
     * @param fromTime 起始时间（含）
     */
    List<ParticipationTrendVO> participationTrend(@Param("fromTime") LocalDateTime fromTime);

    /**
     * 各玩法类型参与人数
     *
     * @param fromTime 起始时间（含）
     */
    List<ParticipationByTypeVO> participationByType(@Param("fromTime") LocalDateTime fromTime);
}
