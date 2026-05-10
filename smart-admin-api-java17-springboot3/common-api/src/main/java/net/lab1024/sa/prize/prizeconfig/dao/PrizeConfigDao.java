package net.lab1024.sa.prize.prizeconfig.dao;

        import java.util.List;
        import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
        import net.lab1024.sa.prize.prizeconfig.domain.form.PrizeConfigQueryForm;
        import net.lab1024.sa.prize.prizeconfig.domain.vo.PrizeConfigVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖品配置表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */
@Mapper
public interface PrizeConfigDao extends BaseMapper<PrizeConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeConfigVO> queryPage(Page<?> page, @Param("queryForm") PrizeConfigQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeConfigVO> queryList(@Param("queryForm") PrizeConfigQueryForm queryForm);

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