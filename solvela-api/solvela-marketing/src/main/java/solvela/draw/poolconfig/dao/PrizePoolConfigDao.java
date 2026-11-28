package solvela.draw.poolconfig.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.domain.dto.PrizePoolConfigDTO;
import solvela.draw.poolconfig.domain.query.PrizePoolConfigQuery;

import java.util.List;

/**
 * 奖池配置 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@Mapper
public interface PrizePoolConfigDao extends BaseMapper<PrizePoolConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolConfigDTO> queryPage(Page<?> page, @Param("queryForm") PrizePoolConfigQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolConfigDTO> queryList(@Param("queryForm") PrizePoolConfigQuery queryForm);

    /**
     * 开启奖池：关闭 → 开启，返回影响行数。
     *
     * WHERE 里的旧状态是并发闸门，不是多余条件：两个运营同时点，
     * 第二个人拿到 rows=0 就知道状态已被别人改过，而不是两个人都以为自己成功了。
     * 迁移见 {@link solvela.enums.PrizePoolStatusEnum#canTransitTo}，与 {@code LotteryConfigDao.markOnline} 同构。
     */
    int markOpen(@Param("id") Long id);

    /** 禁用奖池：开启 → 关闭，返回影响行数 */
    int markClosed(@Param("id") Long id);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);
}