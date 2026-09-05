package solvela.mall.commodity.dao;

import java.util.List;

import solvela.mall.MallCommodity;
import solvela.mall.commodity.domain.query.MallCommodityQuery;
import solvela.mall.commodity.domain.dto.MallCommodityDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商城-商品主表 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */
@Mapper
public interface MallCommodityDao extends BaseMapper<MallCommodity> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallCommodityDTO> queryPage(Page<?> page, @Param("queryForm") MallCommodityQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallCommodityDTO> queryList(@Param("queryForm") MallCommodityQuery queryForm);

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