package solvela.mall.sku.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.mall.sku.domain.form.MallSkuQueryForm;
import solvela.mall.sku.domain.vo.MallSkuVO;

import java.util.List;

/**
 * 商城-库存总览 Service
 *
 * <p><b>只读</b>：改库存在商品编辑页（那里有批量设置，且与价格、状态同一个聚合保存事务）。
 * 两个入口写同一批数据，迟早对不上。
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
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MallSkuVO> list = mallSkuDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }


}
