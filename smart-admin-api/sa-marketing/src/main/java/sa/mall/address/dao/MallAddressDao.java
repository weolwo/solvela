package sa.mall.address.dao;

        import java.util.List;
        import sa.mall.address.domain.entity.MallAddress;
        import sa.mall.address.domain.form.MallAddressQueryForm;
        import sa.mall.address.domain.vo.MallAddressVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城-会员收货地址簿 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */
@Mapper
public interface MallAddressDao extends BaseMapper<MallAddress> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallAddressVO> queryPage(Page<?> page, @Param("queryForm") MallAddressQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallAddressVO> queryList(@Param("queryForm") MallAddressQueryForm queryForm);

}