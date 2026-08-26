package solvela.activity.manager;

import solvela.activity.dao.ActivityConfigDao;
import solvela.activity.ActivityConfig;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 活动配置  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class ActivityConfigManager extends ServiceImpl<ActivityConfigDao, ActivityConfig> {


}
