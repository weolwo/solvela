package net.lab1024.sa.activity.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.activity.domain.entity.ActivityDisplay;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动 C 端展示配置。
 *
 * @Date 2026-08-10
 */
@Mapper
public interface ActivityDisplayDao extends BaseMapper<ActivityDisplay> {

    default ActivityDisplay getByActivityId(Long activityId) {
        return selectOne(new LambdaQueryWrapper<ActivityDisplay>()
                .eq(ActivityDisplay::getActivityId, activityId));
    }
}
