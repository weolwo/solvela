package solvela.mall.favorite.manager;

import solvela.mall.favorite.domain.entity.MallFavorite;
import solvela.mall.favorite.dao.MallFavoriteDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-商品收藏  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallFavoriteManager extends ServiceImpl<MallFavoriteDao, MallFavorite> {


}
