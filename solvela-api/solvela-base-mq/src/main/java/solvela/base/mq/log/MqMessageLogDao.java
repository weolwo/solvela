package solvela.base.mq.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.mq.MqMessageLog;

/**
 * 消息记录的读写。只有 BaseMapper —— 这张表的操作就三种：
 * 插一行（幂等闸门）、标记结果、按 consumer_key 挑失败的重试。
 */
@Mapper
public interface MqMessageLogDao extends BaseMapper<MqMessageLog> {
}
