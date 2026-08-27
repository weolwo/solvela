package solvela.mall.order.dao;

        import java.util.List;

        import solvela.mall.MallOrder;
        import solvela.mall.order.domain.query.MallOrderQuery;
        import solvela.mall.order.domain.dto.MallOrderRankDTO;
        import solvela.mall.order.domain.dto.MallOrderStatDTO;
        import solvela.mall.order.domain.dto.MallOrderDTO;
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
    List<MallOrderDTO> queryPage(Page<?> page, @Param("queryForm") MallOrderQuery queryForm);

    /**
     * 统计：一趟 SQL 出全部指标，条件与列表复用同一段 query_condition_items
     */
    MallOrderStatDTO queryStat(@Param("queryForm") MallOrderQuery queryForm);

    /**
     * 兑换商品排行（按兑换件数）
     */
    List<MallOrderRankDTO> queryCommodityRank(@Param("queryForm") MallOrderQuery queryForm, @Param("topN") int topN);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallOrderDTO> queryList(@Param("queryForm") MallOrderQuery queryForm);

}