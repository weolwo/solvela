package sa.mall.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.mall.order.dao.MallOrderDao;
import sa.mall.order.domain.entity.MallOrder;
import sa.mall.order.domain.form.MallOrderAddForm;
import sa.mall.order.domain.form.MallOrderQueryForm;
import sa.mall.order.domain.form.MallOrderUpdateForm;
import sa.mall.order.domain.vo.MallOrderVO;

import java.util.List;

/**
 * 商城-兑换订单 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallOrderService {

    private final MallOrderDao mallOrderDao;

    /**
     * 分页查询
     */
    public PageResult<MallOrderVO> queryPage(MallOrderQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallOrderVO> list = mallOrderDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MallOrderAddForm addForm) {
        MallOrder mallOrder = SmartBeanUtil.copy(addForm, MallOrder.class);
        mallOrderDao.insert(mallOrder);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MallOrderUpdateForm updateForm) {
        MallOrder mallOrder = SmartBeanUtil.copy(updateForm, MallOrder.class);
        mallOrderDao.updateById(mallOrder);
        return ResponseDTO.ok();
    }

}
