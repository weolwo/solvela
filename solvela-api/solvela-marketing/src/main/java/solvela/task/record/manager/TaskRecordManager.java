package solvela.task.record.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.record.domain.entity.TaskRecord;
import org.springframework.stereotype.Service;
/**
 * 任务记录表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskRecordManager extends ServiceImpl<TaskRecordDao, TaskRecord> {


}
