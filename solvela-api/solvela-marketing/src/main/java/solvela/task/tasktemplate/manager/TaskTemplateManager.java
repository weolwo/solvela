package solvela.task.tasktemplate.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import solvela.task.tasktemplate.dao.TaskTemplateDao;
import solvela.task.TaskTemplate;
import org.springframework.stereotype.Service;
/**
 * 任务模板表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskTemplateManager extends ServiceImpl<TaskTemplateDao, TaskTemplate> {


}
