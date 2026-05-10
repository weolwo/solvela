package net.lab1024.sa.prize.prizelog.dao;

        import java.util.List;
        import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
        import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogQueryForm;
        import net.lab1024.sa.prize.prizelog.domain.vo.PrizeLogVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖励记录表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@Mapper
public interface PrizeLogDao extends BaseMapper<PrizeLog> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryPage(Page<?> page, @Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryList(@Param("queryForm") PrizeLogQueryForm queryForm);

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