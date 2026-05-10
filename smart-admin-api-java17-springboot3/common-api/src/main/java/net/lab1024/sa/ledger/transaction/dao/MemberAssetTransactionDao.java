package net.lab1024.sa.ledger.transaction.dao;

        import java.util.List;
        import net.lab1024.sa.ledger.transaction.domain.entity.MemberAssetTransaction;
        import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionQueryForm;
        import net.lab1024.sa.ledger.transaction.domain.vo.MemberAssetTransactionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 交易明细表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */
@Mapper
public interface MemberAssetTransactionDao extends BaseMapper<MemberAssetTransaction> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberAssetTransactionVO> queryPage(Page<?> page, @Param("queryForm") MemberAssetTransactionQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberAssetTransactionVO> queryList(@Param("queryForm") MemberAssetTransactionQueryForm queryForm);

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