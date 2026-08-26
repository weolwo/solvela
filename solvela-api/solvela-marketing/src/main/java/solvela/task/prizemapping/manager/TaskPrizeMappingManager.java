package solvela.task.prizemapping.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import solvela.task.prizemapping.dao.TaskPrizeMappingDao;
import solvela.task.TaskPrizeMapping;
import org.springframework.stereotype.Service;
/**
 * 任务阶段与奖励映射表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskPrizeMappingManager extends ServiceImpl<TaskPrizeMappingDao, TaskPrizeMapping> {


}
