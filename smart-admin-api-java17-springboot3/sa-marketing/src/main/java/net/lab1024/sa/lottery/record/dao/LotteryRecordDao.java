package net.lab1024.sa.lottery.record.dao;

        import java.util.List;
        import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
        import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
        import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 用户号码记录 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@Mapper
public interface LotteryRecordDao extends BaseMapper<LotteryRecord> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryRecordVO> queryPage(Page<?> page, @Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<LotteryRecordVO> queryList(@Param("queryForm") LotteryRecordQueryForm queryForm);

    /**
     * 某期已发出的最大游标，供 Redis 冷启动回源用。
     *
     * ⚠️ 必须取 MAX 而不是 COUNT：发号过程中失败会留下空洞（游标消耗了但记录没落库），
     * 此时 COUNT 小于真实游标，回填后会重发已发过的号码、直接撞 uk_issue_ticket。
     *
     * @return 无记录时返回 null（由调用方兜底成 0）
     */
    Long selectMaxSequenceNo(@Param("lotteryCode") String lotteryCode, @Param("issueNo") String issueNo);

    /**
     * 我的号码：按奖级升序（一等奖在前、未中奖的 99 沉底），同奖级按最新领取在前
     */
    List<LotteryRecord> selectMyTickets(@Param("lotteryCode") String lotteryCode,
                                        @Param("issueNo") String issueNo,
                                        @Param("memberName") String memberName);

}