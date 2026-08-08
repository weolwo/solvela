package net.lab1024.sa.task.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.task.record.dao.TaskRecordDao;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.record.domain.form.TaskRecordAddForm;
import net.lab1024.sa.task.record.domain.form.TaskRecordQueryForm;
import net.lab1024.sa.task.record.domain.form.TaskRecordUpdateForm;
import net.lab1024.sa.task.record.domain.vo.TaskRecordVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务记录表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskRecordService {

    private final TaskRecordDao taskRecordDao;

    /**
     * 分页查询
     */
    public PageResult<TaskRecordVO> queryPage(TaskRecordQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TaskRecordVO> list = taskRecordDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(TaskRecordAddForm addForm) {
        TaskRecord taskRecord = SmartBeanUtil.copy(addForm, TaskRecord.class);
        taskRecordDao.insert(taskRecord);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(TaskRecordUpdateForm updateForm) {
        TaskRecord taskRecord = SmartBeanUtil.copy(updateForm, TaskRecord.class);
        taskRecordDao.updateById(taskRecord);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        taskRecordDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        taskRecordDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
