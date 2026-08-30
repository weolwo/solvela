package solvela.dispatch.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.dispatch.PrizeDispatchOutbox;

/**
 * outbox 的读写。刻意只有 BaseMapper —— 这张表只有三种操作：
 * 写一行、标记已投递、扫待投递的。复杂查询意味着有人在拿它当业务表用。
 */
@Mapper
public interface PrizeDispatchOutboxDao extends BaseMapper<PrizeDispatchOutbox> {
}
