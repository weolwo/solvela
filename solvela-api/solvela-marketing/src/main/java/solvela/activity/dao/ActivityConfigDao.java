package solvela.activity.dao;

import java.util.List;

import solvela.activity.ActivityConfig;
import solvela.activity.domain.query.ActivityConfigQuery;
import solvela.activity.domain.dto.ActivityConfigDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动配置 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@Mapper
public interface ActivityConfigDao extends BaseMapper<ActivityConfig> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ActivityConfigDTO> queryPage(Page<?> page, @Param("queryForm") ActivityConfigQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ActivityConfigDTO> queryList(@Param("queryForm") ActivityConfigQuery queryForm);

    /*
     * ==================== 状态闸门 ====================
     *
     * 🔴 带旧状态条件，返回影响行数：0 表示状态已被别人改过。迁移见 ActivityStatusEnum#canTransitTo，
     * ActivityConfigStatusSqlTest 断言 SQL 与状态机一一对应。不要改回「new 一个实体 set 状态再 updateById」。
     */

    /** 上线：未开始 / 下线 → 上线 */
    int markOnline(@Param("id") Long id);

    /** 下线：未开始 / 上线 → 下线 */
    int markOffline(@Param("id") Long id);

    /** 物理删除，只删未开始的活动 */
    int deleteNotStarted(@Param("id") Long id);

    /** 批量物理删除，只删未开始的活动；返回值小于入参条数说明有活动在校验之后被改了状态 */
    int deleteNotStartedBatch(@Param("idList") List<Long> idList);
}