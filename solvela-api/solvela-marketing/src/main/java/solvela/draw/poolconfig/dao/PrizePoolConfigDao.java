package solvela.draw.poolconfig.dao;

        import java.util.List;

        import solvela.draw.PrizePoolConfig;
        import solvela.draw.poolconfig.domain.query.PrizePoolConfigQuery;
        import solvela.draw.poolconfig.domain.dto.PrizePoolConfigDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
     * 条件更新奖池开关，返回影响行数。
     *
     * WHERE status = #{from} 是并发闸门，不是多余条件：两个运营同时点禁用，
     * 第二个人拿到 rows=0 就知道状态已被别人改过，而不是两个人都以为自己成功了。
     * 与 {@code LotteryConfigDao.updateStatus} 同构 —— 这类开关全项目一个形状。
     */
    int updateStatus(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

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