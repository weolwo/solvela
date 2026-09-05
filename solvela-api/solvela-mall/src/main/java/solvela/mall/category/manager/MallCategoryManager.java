package solvela.mall.category.manager;

import solvela.mall.category.dao.MallCategoryDao;
import solvela.mall.MallCategory;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-商品分类  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallCategoryManager extends ServiceImpl<MallCategoryDao, MallCategory> {


}
