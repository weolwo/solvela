package sa.mall.favorite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.mall.favorite.dao.MallFavoriteDao;
import sa.mall.favorite.domain.form.MallFavoriteQueryForm;
import sa.mall.favorite.domain.vo.MallFavoriteVO;

import java.util.List;

/**
 * 商城-商品收藏 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:34:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallFavoriteService {

    private final MallFavoriteDao mallFavoriteDao;

    /**
     * 分页查询
     */
    public PageResult<MallFavoriteVO> queryPage(MallFavoriteQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallFavoriteVO> list = mallFavoriteDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }


}
