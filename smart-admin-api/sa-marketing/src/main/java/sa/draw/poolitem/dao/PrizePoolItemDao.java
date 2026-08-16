package sa.draw.poolitem.dao;

        import java.util.List;
        import sa.draw.poolitem.domain.entity.PrizePoolItem;
        import sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
        import sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖池奖项库 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@Mapper
public interface PrizePoolItemDao extends BaseMapper<PrizePoolItem> {

    /**
     * 已发库存 +1（条件更新：不限量或未发满才生效，返回0表示DB层面无库存——防超发第二道防线）
     */
    int increaseUsedStock(@Param("id") Long id);

    /**
     * 数据库当前时间。
     *
     * 奖池的重置周期（每天/每周/每月）要据此算出单人限领的计数桶，
     * 不能用 JVM 的 {@code LocalDateTime.now()} —— 铁律 9/10：全系统只认数据库一个时钟。
     * 多实例部署时各节点 JVM 时钟未必一致，跨零点那一刻会出现
     * 「A 节点认为还是昨天、B 节点认为已是今天」，同一个用户在两个桶里各拿一次额度。
     */
    java.time.LocalDateTime selectDbNow();

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolItemVO> queryPage(Page<?> page, @Param("queryForm") PrizePoolItemQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizePoolItemVO> queryList(@Param("queryForm") PrizePoolItemQueryForm queryForm);

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