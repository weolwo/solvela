package sa.mall.category.manager;

import sa.mall.category.dao.MallCategoryDao;
import sa.mall.category.domain.entity.MallCategory;

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
