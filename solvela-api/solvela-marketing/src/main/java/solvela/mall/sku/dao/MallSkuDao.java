package solvela.mall.sku.dao;

        import java.util.List;

        import solvela.mall.MallSku;
        import solvela.mall.sku.domain.query.MallSkuQuery;
        import solvela.mall.sku.domain.dto.MallSkuDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城-SKU与库存 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@Mapper
public interface MallSkuDao extends BaseMapper<MallSku> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallSkuDTO> queryPage(Page<?> page, @Param("queryForm") MallSkuQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallSkuDTO> queryList(@Param("queryForm") MallSkuQuery queryForm);

}