package net.lab1024.sa.task.tasktemplate.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.task.tasktemplate.dao.TaskTemplateDao;
import net.lab1024.sa.task.tasktemplate.domain.entity.TaskTemplate;
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
