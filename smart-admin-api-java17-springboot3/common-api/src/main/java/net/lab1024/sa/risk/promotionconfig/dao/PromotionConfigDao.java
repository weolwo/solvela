package net.lab1024.sa.risk.promotionconfig.dao;

        import java.util.List;
        import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
        import net.lab1024.sa.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
        import net.lab1024.sa.risk.promotionconfig.domain.vo.PromotionConfigVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 优惠配置表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */
@Mapper
public interface PromotionConfigDao extends BaseMapper<PromotionConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PromotionConfigVO> queryPage(Page<?> page, @Param("queryForm") PromotionConfigQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PromotionConfigVO> queryList(@Param("queryForm") PromotionConfigQueryForm queryForm);

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