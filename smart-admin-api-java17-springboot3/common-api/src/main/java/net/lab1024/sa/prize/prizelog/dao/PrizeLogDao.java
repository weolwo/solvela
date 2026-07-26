package net.lab1024.sa.prize.prizelog.dao;

        import java.util.List;
        import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
        import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogQueryForm;
        import net.lab1024.sa.prize.prizelog.domain.vo.PrizeLogVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 奖励记录表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@Mapper
public interface PrizeLogDao extends BaseMapper<PrizeLog> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryPage(Page<?> page, @Param("queryForm") PrizeLogQueryForm queryForm);

    /**
     * 按业务单号回写派发终态（0-等待, 1-成功, 2-失败）
     *
     * 资产下发在提案事务提交后才执行，结果回不到 PrizeDispatchHandler 的调用栈，
     * 只能由资产分发引擎反向更新，否则发奖记录会停在「成功」而用户实际没收到。
     * 只更新仍处于 0-等待执行 的记录，避免覆盖人工订正过的终态。
     *
     * @return 更新行数
     */
    int updateStatusByExternalBizNo(@Param("externalBizNo") String externalBizNo,
                                    @Param("status") Integer status,
                                    @Param("failReason") String failReason);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PrizeLogVO> queryList(@Param("queryForm") PrizeLogQueryForm queryForm);

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