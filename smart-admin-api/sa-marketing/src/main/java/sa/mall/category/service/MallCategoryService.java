package sa.mall.category.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.mall.category.dao.MallCategoryDao;
import sa.mall.category.domain.entity.MallCategory;
import sa.mall.category.domain.form.MallCategoryAddForm;
import sa.mall.category.domain.form.MallCategoryQueryForm;
import sa.mall.category.domain.form.MallCategoryUpdateForm;
import sa.mall.category.domain.vo.MallCategoryVO;

import java.util.List;

/**
 * 商城-商品分类 Service
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallCategoryService {

    private final MallCategoryDao mallCategoryDao;

    /**
     * 分页查询
     */
    public PageResult<MallCategoryVO> queryPage(MallCategoryQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MallCategoryVO> list = mallCategoryDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MallCategoryAddForm addForm) {
        MallCategory mallCategory = SmartBeanUtil.copy(addForm, MallCategory.class);
        mallCategoryDao.insert(mallCategory);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MallCategoryUpdateForm updateForm) {
        MallCategory mallCategory = SmartBeanUtil.copy(updateForm, MallCategory.class);
        mallCategoryDao.updateById(mallCategory);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (idList !=null && !idList.isEmpty()){
            return ResponseDTO.ok();
        }

        mallCategoryDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        mallCategoryDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
