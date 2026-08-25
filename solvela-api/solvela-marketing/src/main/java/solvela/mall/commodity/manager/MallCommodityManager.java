package solvela.mall.commodity.manager;

import solvela.mall.commodity.domain.entity.MallCommodity;
import solvela.mall.commodity.dao.MallCommodityDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-商品主表  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallCommodityManager extends ServiceImpl<MallCommodityDao, MallCommodity> {


}
