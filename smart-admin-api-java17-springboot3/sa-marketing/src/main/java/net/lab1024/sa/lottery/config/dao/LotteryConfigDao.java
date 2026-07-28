package net.lab1024.sa.lottery.config.dao;

        import java.util.List;
        import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
        import net.lab1024.sa.lottery.config.domain.form.LotteryConfigQueryForm;
        import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    List<LotteryConfigVO> queryPage(Page<?> page, @Param("queryForm") LotteryConfigQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryConfigVO> queryList(@Param("queryForm") LotteryConfigQueryForm queryForm);

    /**
     * 状态条件流转，做并发闸门用。
     * 把「当前状态必须是 from」压进 WHERE，两个人同时点第二次会拿到 rows=0，
     * 而不是两次都「成功」。与提案域审批、开奖闸门是同一套做法。
     *
     * @return 影响行数，0 表示状态已被别人改过
     */
    int updateStatus(@Param("id") Long id, @Param("from") Integer from, @Param("to") Integer to);

            // ----- 物理删除 -----
                /**
                 * 单个物理删除
                 */
                long deleteById(@Param("id") Long id);

                /**
                 * 批量物理删除
                 */
                void batchDelete(@Param("idList") List<Long> idList);
}