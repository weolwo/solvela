package sa.mall.commodity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.mall.commodity.dao.MallCommodityDao;
import sa.mall.commodity.domain.entity.MallCommodity;
import sa.mall.commodity.domain.form.MallCommodityAddForm;
import sa.mall.commodity.domain.form.MallCommodityQueryForm;
import sa.mall.commodity.domain.form.MallCommodityUpdateForm;
import sa.mall.commodity.domain.vo.MallCommodityVO;

import java.util.List;

/**
 * 商城-商品主表 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallCommodityService {

    private final MallCommodityDao mallCommodityDao;

    /**
     * 分页查询
     */
    public PageResult<MallCommodityVO> queryPage(MallCommodityQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallCommodityVO> list = mallCommodityDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MallCommodityAddForm addForm) {
        MallCommodity mallCommodity = SmartBeanUtil.copy(addForm, MallCommodity.class);
        mallCommodityDao.insert(mallCommodity);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MallCommodityUpdateForm updateForm) {
        MallCommodity mallCommodity = SmartBeanUtil.copy(updateForm, MallCommodity.class);
        mallCommodityDao.updateById(mallCommodity);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (idList != null && !idList.isEmpty()) {
            return ResponseDTO.ok();
        }

        mallCommodityDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        mallCommodityDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
