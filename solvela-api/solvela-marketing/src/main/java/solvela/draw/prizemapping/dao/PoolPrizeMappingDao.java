package solvela.draw.prizemapping.dao;

        import java.util.List;

        import solvela.draw.PoolPrizeMapping;
        import solvela.draw.prizemapping.domain.form.PoolPrizeMappingQueryForm;
        import solvela.draw.prizemapping.domain.vo.PoolPrizeMappingVO;
        import solvela.base.dao.CustomizedBaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖池奖项映射 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@Mapper
public interface PoolPrizeMappingDao extends CustomizedBaseMapper<PoolPrizeMapping> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PoolPrizeMappingVO> queryPage(Page<?> page, @Param("queryForm") PoolPrizeMappingQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PoolPrizeMappingVO> queryList(@Param("queryForm") PoolPrizeMappingQueryForm queryForm);

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