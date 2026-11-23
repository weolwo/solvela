package solvela.lottery.config.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.domain.dto.LotteryConfigDTO;
import solvela.lottery.config.domain.query.LotteryConfigQuery;

import java.util.List;

/**
 * 彩票配置 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@Mapper
public interface LotteryConfigDao extends BaseMapper<LotteryConfig> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryConfigDTO> queryPage(Page<?> page, @Param("queryForm") LotteryConfigQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryConfigDTO> queryList(@Param("queryForm") LotteryConfigQuery queryForm);

    /**
     * 上线：下线 → 上线。旧状态压进 WHERE，两个人同时点第二次会拿到 0，而不是两次都「成功」。
     * 迁移见 {@link solvela.enums.LotteryConfigStatusEnum#canTransitTo}。
     *
     * @return 影响行数，0 表示状态已被别人改过
     */
    int markOnline(@Param("id") Long id);

    /**
     * 下线：上线 → 下线。
     *
     * @return 影响行数，0 表示状态已被别人改过
     */
    int markOffline(@Param("id") Long id);

    // 物理删除已移除：t_lottery_record 里存着 lottery_code，删配置会让用户手里已发出的号码断链。
    // 停售走 updateStatus 下线 —— 见 LotteryConfigController 的类注释
}