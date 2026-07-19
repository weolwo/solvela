package net.lab1024.sa.task.taskconfig.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.task.taskconfig.dao.TaskConfigDao;
import net.lab1024.sa.task.taskconfig.domain.entity.TaskConfig;
import org.springframework.stereotype.Service;
/**
 * 任务配置表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskConfigManager extends ServiceImpl<TaskConfigDao, TaskConfig> {


}
