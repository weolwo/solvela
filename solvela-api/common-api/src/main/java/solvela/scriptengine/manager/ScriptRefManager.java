package solvela.scriptengine.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.scriptengine.dao.ScriptRefDao;
import solvela.scriptengine.domain.entity.ScriptRef;

/**
 * 脚本引用关系 Manager
 */
@RequiredArgsConstructor
@Service
public class ScriptRefManager extends ServiceImpl<ScriptRefDao, ScriptRef> {
}
