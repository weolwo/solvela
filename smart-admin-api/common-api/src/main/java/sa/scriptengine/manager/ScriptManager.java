package sa.scriptengine.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.scriptengine.dao.ScriptDao;
import sa.scriptengine.domain.entity.Script;

/**
 * 脚本注册表 Manager
 */
@RequiredArgsConstructor
@Service
public class ScriptManager extends ServiceImpl<ScriptDao, Script> {
}
