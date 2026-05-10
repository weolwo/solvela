package net.lab1024.sa.task.prizemapping.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.task.prizemapping.dao.TaskPrizeMappingDao;
import net.lab1024.sa.task.prizemapping.domain.entity.TaskPrizeMapping;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingAddForm;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingQueryForm;
import net.lab1024.sa.task.prizemapping.domain.form.TaskPrizeMappingUpdateForm;
import net.lab1024.sa.task.prizemapping.domain.vo.TaskPrizeMappingVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务阶段与奖励映射表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskPrizeMappingService {

    private final TaskPrizeMappingDao taskPrizeMappingDao;

    /**
     * 分页查询
     */
    public PageResult<TaskPrizeMappingVO> queryPage(TaskPrizeMappingQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TaskPrizeMappingVO> list = taskPrizeMappingDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(TaskPrizeMappingAddForm addForm) {
        TaskPrizeMapping taskPrizeMapping = SmartBeanUtil.copy(addForm, TaskPrizeMapping.class);
        taskPrizeMappingDao.insert(taskPrizeMapping);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(TaskPrizeMappingUpdateForm updateForm) {
        TaskPrizeMapping taskPrizeMapping = SmartBeanUtil.copy(updateForm, TaskPrizeMapping.class);
        taskPrizeMappingDao.updateById(taskPrizeMapping);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        taskPrizeMappingDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        taskPrizeMappingDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
