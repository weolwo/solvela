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

}