package solvela.draw.drawconfig.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.draw.DrawConfig;
import solvela.draw.drawconfig.dao.DrawConfigDao;

/**
 * 抽奖配置 Manager
 */
@RequiredArgsConstructor
@Service
public class DrawConfigManager extends ServiceImpl<DrawConfigDao, DrawConfig> {
}
