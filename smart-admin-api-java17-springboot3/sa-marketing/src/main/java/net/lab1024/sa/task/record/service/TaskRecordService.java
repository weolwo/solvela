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
import net.lab1024.sa.task.record.domain.form.TaskRecordStatusUpdateForm;
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
     * 任务记录状态：3-已过期（对齐 TaskConst.RECORD_STATUS_EXPIRED）
     */
    private static final Integer STATUS_EXPIRED = 3;

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
     * 任务记录 批量禁用：置为 3-已过期。
     *
     * <p>t_task_record.status 没有「禁用」这一档，「让这条记录不再推进、不再发奖」在库里
     * 只有「已过期」一个终态可表达（过期任务本来也是这么收口的）。
     * 故这里只放行 3，不接受其它值 —— 允许管理端随手把记录改回「进行中」或「已发奖」，
     * 等于给了一条绕过运行态直接改结果的路。
     */
    public ResponseDTO<String> updateStatus(TaskRecordStatusUpdateForm form) {
        if (!STATUS_EXPIRED.equals(form.getStatus())) {
            return ResponseDTO.userErrorParam("任务记录只支持置为 3-已过期（即管理端的「禁用」）");
        }
        for (Long id : form.getIdList()) {
            TaskRecord update = new TaskRecord();
            update.setId(id);
            update.setStatus(form.getStatus());
            taskRecordDao.updateById(update);
        }
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
