package solvela.scriptengine.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.scriptengine.Script;

/**
 * 脚本注册表 Dao
 */
@Mapper
public interface ScriptDao extends BaseMapper<Script> {
}
