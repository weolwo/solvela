package sa.mall.sku.dao;

        import java.util.List;
        import sa.mall.sku.domain.entity.MallSku;
        import sa.mall.sku.domain.form.MallSkuQueryForm;
        import sa.mall.sku.domain.vo.MallSkuVO;
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
    List<MallSkuVO> queryPage(Page<?> page, @Param("queryForm") MallSkuQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallSkuVO> queryList(@Param("queryForm") MallSkuQueryForm queryForm);

}