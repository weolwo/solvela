package solvela.admin.module.system.position.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import solvela.admin.module.system.position.dao.PositionDao;
import solvela.admin.module.system.position.domain.entity.PositionEntity;
import solvela.admin.module.system.position.domain.form.PositionAddForm;
import solvela.admin.module.system.position.domain.form.PositionQueryForm;
import solvela.admin.module.system.position.domain.form.PositionUpdateForm;
import solvela.admin.module.system.position.domain.vo.PositionVO;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 职务表 Service
 *
 * @Author kaiyun
 * @Date 2024-06-23 23:31:38
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Service
public class PositionService {

    @Resource
    private PositionDao positionDao;

    /**
     * 分页查询
     *
     * @param queryForm
     * @return
     */
    public PageResult<PositionVO> queryPage(PositionQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PositionVO> list = positionDao.queryPage(page, queryForm);
        PageResult<PositionVO> pageResult = SolvelaPageUtil.convert2PageResult(page, list);
        return pageResult;
    }

    /**
     * 添加
     */
    public void add(PositionAddForm addForm) {
        PositionEntity positionEntity = SolvelaBeanUtil.copy(addForm, PositionEntity.class);
        positionDao.insert(positionEntity);
    }

    /**
     * 更新
     *
     * @param updateForm
     * @return
     */
    public void update(PositionUpdateForm updateForm) {
        PositionEntity positionEntity = SolvelaBeanUtil.copy(updateForm, PositionEntity.class);
        positionDao.updateById(positionEntity);
    }

    /**
     * 批量删除
     *
     * @param idList
     * @return
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        positionDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long positionId) {
        if (null == positionId){
            return;
        }

        positionDao.deleteById(positionId);
    }

    /**
     * 分页查询
     *
     * @return
     */
    public List<PositionVO> queryList() {
        List<PositionVO> list = positionDao.queryList(Boolean.FALSE);
        return list;
    }
}
