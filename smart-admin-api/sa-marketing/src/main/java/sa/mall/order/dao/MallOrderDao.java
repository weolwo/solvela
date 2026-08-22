package sa.mall.order.dao;

        import java.util.List;
        import sa.mall.order.domain.entity.MallOrder;
        import sa.mall.order.domain.form.MallOrderQueryForm;
        import sa.mall.order.domain.vo.MallOrderVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 商城-兑换订单 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@Mapper
public interface MallOrderDao extends BaseMapper<MallOrder> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallOrderVO> queryPage(Page<?> page, @Param("queryForm") MallOrderQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallOrderVO> queryList(@Param("queryForm") MallOrderQueryForm queryForm);

}