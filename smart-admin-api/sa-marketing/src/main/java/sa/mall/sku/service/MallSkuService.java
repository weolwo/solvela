package sa.mall.sku.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.mall.sku.dao.MallSkuDao;
import sa.mall.sku.domain.form.MallSkuQueryForm;
import sa.mall.sku.domain.vo.MallSkuVO;

import java.util.List;

/**
 * 商城-SKU与库存 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallSkuService {

    private final MallSkuDao mallSkuDao;

    /**
     * 分页查询
     */
    public PageResult<MallSkuVO> queryPage(MallSkuQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallSkuVO> list = mallSkuDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }


}
