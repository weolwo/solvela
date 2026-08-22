package sa.mall.exchangelimit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.mall.exchangelimit.dao.MallExchangeLimitDao;
import sa.mall.exchangelimit.domain.entity.MallExchangeLimit;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitAddForm;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitQueryForm;
import sa.mall.exchangelimit.domain.form.MallExchangeLimitUpdateForm;
import sa.mall.exchangelimit.domain.vo.MallExchangeLimitVO;

import java.util.List;

/**
 * 商城-会员限兑计数 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallExchangeLimitService {

    private final MallExchangeLimitDao mallExchangeLimitDao;

    /**
     * 分页查询
     */
    public PageResult<MallExchangeLimitVO> queryPage(MallExchangeLimitQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallExchangeLimitVO> list = mallExchangeLimitDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MallExchangeLimitAddForm addForm) {
        MallExchangeLimit mallExchangeLimit = SmartBeanUtil.copy(addForm, MallExchangeLimit.class);
        mallExchangeLimitDao.insert(mallExchangeLimit);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MallExchangeLimitUpdateForm updateForm) {
        MallExchangeLimit mallExchangeLimit = SmartBeanUtil.copy(updateForm, MallExchangeLimit.class);
        mallExchangeLimitDao.updateById(mallExchangeLimit);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (idList !=null && !idList.isEmpty()){
            return ResponseDTO.ok();
        }

        mallExchangeLimitDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        mallExchangeLimitDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
