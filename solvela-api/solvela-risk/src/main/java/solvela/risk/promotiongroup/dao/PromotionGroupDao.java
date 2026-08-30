package solvela.risk.promotiongroup.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.risk.PromotionGroup;
import solvela.risk.promotiongroup.domain.dto.PromotionGroupDTO;
import solvela.risk.promotiongroup.domain.query.PromotionGroupQuery;

import java.util.List;

/**
 * 优惠配置分组 Dao
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Mapper
public interface PromotionGroupDao extends BaseMapper<PromotionGroup> {

    /**
     * 分页查询。
     *
     * <p>列表要显示「这个组配了几种类型、其中几种是启用的」，
     * 那是子表上的聚合，所以走手写 SQL 而不是 MyBatis-Plus 的通用分页。
     */
    List<PromotionGroupDTO> queryPage(Page<?> page, @Param("queryForm") PromotionGroupQuery queryForm);
}
