package net.lab1024.sa.draw.poolitem.dao;

        import java.util.List;
        import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
        import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
        import net.lab1024.sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖池奖项库 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@Mapper
public interface PrizePoolItemDao extends BaseMapper<PrizePoolItem> {

    /**
     * 已发库存 +1（条件更新：不限量或未发满才生效，返回0表示DB层面无库存——防超发第二道防线）
     */
    int increaseUsedStock(@Param("id") Long id);

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolItemVO> queryPage(Page<?> page, @Param("queryForm") PrizePoolItemQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolItemVO> queryList(@Param("queryForm") PrizePoolItemQueryForm queryForm);

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