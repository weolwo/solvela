package solvela.mall.category.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.mall.MallCategory;
import solvela.mall.category.domain.query.MallCategoryQuery;
import solvela.mall.category.domain.dto.MallCategoryDTO;

import java.util.List;

/**
 * 商城-商品分类 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */
@Mapper
public interface MallCategoryDao extends BaseMapper<MallCategory> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallCategoryDTO> queryPage(Page<?> page, @Param("queryForm") MallCategoryQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallCategoryDTO> queryList(@Param("queryForm") MallCategoryQuery queryForm);

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