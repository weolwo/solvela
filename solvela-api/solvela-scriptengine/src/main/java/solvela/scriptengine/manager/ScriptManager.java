package solvela.scriptengine.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.scriptengine.dao.ScriptDao;
import solvela.scriptengine.Script;

/**
 * 脚本注册表 Manager
 */
@RequiredArgsConstructor
@Service
public class ScriptManager extends ServiceImpl<ScriptDao, Script> {
}
