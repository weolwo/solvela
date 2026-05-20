package net.lab1024.sa.ledger.wallet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.ledger.wallet.domain.entity.MemberWallet;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import net.lab1024.sa.ledger.wallet.domain.vo.MemberWalletVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员钱包表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */
@Mapper
public interface MemberWalletDao extends BaseMapper<MemberWallet> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberWalletVO> queryPage(Page<?> page, @Param("queryForm") MemberWalletQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberWalletVO> queryList(@Param("queryForm") MemberWalletQueryForm queryForm);

    /**
     * 更加会员名查询钱包
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    MemberWallet getMemberByMemberName(String memberName);

    // ----- 物理删除 -----

    /**
     * 单个物理删除
     */
    long deleteById(@Param("id") Long id);

    /**
     * 批量物理删除
     */
    void batchDelete(@Param("idList") List<Long> idList);

    /**
     * 更新余额
     * @param id
     * @param amount
     * @param version
     * @return
     */
    int addCashBalanceWithVersion(@Param("id") Long id, @Param("amount")BigDecimal amount, @Param("version")Integer version);
}