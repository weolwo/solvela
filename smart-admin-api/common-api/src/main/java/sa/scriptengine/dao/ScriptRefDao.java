package sa.scriptengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import sa.scriptengine.domain.entity.ScriptRef;

/**
 * 脚本引用关系 Dao
 */
@Mapper
public interface ScriptRefDao extends BaseMapper<ScriptRef> {
}
