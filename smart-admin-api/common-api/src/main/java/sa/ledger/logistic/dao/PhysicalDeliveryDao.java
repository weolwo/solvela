package sa.ledger.logistic.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import sa.ledger.logistic.domain.entity.PhysicalDelivery;
import sa.ledger.logistic.domain.form.PhysicalDeliveryQueryForm;
import sa.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发货物流表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@Mapper
public interface PhysicalDeliveryDao extends BaseMapper<PhysicalDelivery> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PhysicalDeliveryVO> queryPage(Page<?> page, @Param("queryForm") PhysicalDeliveryQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PhysicalDeliveryVO> queryList(@Param("queryForm") PhysicalDeliveryQueryForm queryForm);

    // ----- 物理删除 -----

    /**
     * 单个物理删除
     */
    long discardById(@Param("id") Long id);

    /**
     * 批量物理删除
     */
    void discardBatchIds(@Param("idList") List<Long> idList);
}