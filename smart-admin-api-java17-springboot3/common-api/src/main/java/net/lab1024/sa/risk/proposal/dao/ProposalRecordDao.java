package net.lab1024.sa.risk.proposal.dao;

        import java.util.List;
        import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
        import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
        import net.lab1024.sa.risk.proposal.domain.vo.ProposalRecordVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 提案表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@Mapper
public interface ProposalRecordDao extends BaseMapper<ProposalRecord> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordVO> queryPage(Page<?> page, @Param("queryForm") ProposalRecordQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<ProposalRecordVO> queryList(@Param("queryForm") ProposalRecordQueryForm queryForm);

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