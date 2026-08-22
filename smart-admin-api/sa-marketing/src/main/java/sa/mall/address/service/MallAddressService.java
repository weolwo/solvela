package sa.mall.address.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.mall.address.dao.MallAddressDao;
import sa.mall.address.domain.entity.MallAddress;
import sa.mall.address.domain.form.MallAddressAddForm;
import sa.mall.address.domain.form.MallAddressQueryForm;
import sa.mall.address.domain.form.MallAddressUpdateForm;
import sa.mall.address.domain.vo.MallAddressVO;

import java.util.List;

/**
 * 商城-会员收货地址簿 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallAddressService {

    private final MallAddressDao mallAddressDao;

    /**
     * 分页查询
     */
    public PageResult<MallAddressVO> queryPage(MallAddressQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallAddressVO> list = mallAddressDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MallAddressAddForm addForm) {
        MallAddress mallAddress = SmartBeanUtil.copy(addForm, MallAddress.class);
        mallAddressDao.insert(mallAddress);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MallAddressUpdateForm updateForm) {
        MallAddress mallAddress = SmartBeanUtil.copy(updateForm, MallAddress.class);
        mallAddressDao.updateById(mallAddress);
        return ResponseDTO.ok();
    }

}
