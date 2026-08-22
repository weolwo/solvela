package sa.mall.exchangelimit.dao;

        import java.util.List;
        import sa.mall.exchangelimit.domain.entity.MallExchangeLimit;
        import sa.mall.exchangelimit.domain.form.MallExchangeLimitQueryForm;
        import sa.mall.exchangelimit.domain.vo.MallExchangeLimitVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 商城-会员限兑计数 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */
@Mapper
public interface MallExchangeLimitDao extends BaseMapper<MallExchangeLimit> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallExchangeLimitVO> queryPage(Page<?> page, @Param("queryForm") MallExchangeLimitQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallExchangeLimitVO> queryList(@Param("queryForm") MallExchangeLimitQueryForm queryForm);

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