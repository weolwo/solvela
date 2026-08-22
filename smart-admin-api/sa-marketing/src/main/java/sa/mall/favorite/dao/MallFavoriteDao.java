package sa.mall.favorite.dao;

        import java.util.List;
        import sa.mall.favorite.domain.entity.MallFavorite;
        import sa.mall.favorite.domain.form.MallFavoriteQueryForm;
        import sa.mall.favorite.domain.vo.MallFavoriteVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 商城-商品收藏 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@Mapper
public interface MallFavoriteDao extends BaseMapper<MallFavorite> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallFavoriteVO> queryPage(Page<?> page, @Param("queryForm") MallFavoriteQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallFavoriteVO> queryList(@Param("queryForm") MallFavoriteQueryForm queryForm);

}