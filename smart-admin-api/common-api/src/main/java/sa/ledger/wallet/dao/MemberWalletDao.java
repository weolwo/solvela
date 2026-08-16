package sa.ledger.wallet.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import sa.ledger.wallet.domain.entity.MemberWallet;
import sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import sa.ledger.wallet.domain.vo.MemberWalletVO;
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
     * 根据会员名 + 资产类型查询钱包账户（一行一种资产）
     *
     * @param memberName 会员名
     * @param assetType  资产类型 PrizeTypeEnum
     * @return 钱包账户
     */
    MemberWallet getByMemberNameAndAssetType(@Param("memberName") String memberName, @Param("assetType") String assetType);

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
     * 增加余额（乐观锁），资产类型由所在行决定，任何资产通用
     * @param id
     * @param amount
     * @param version
     * @return
     */
    int addBalanceWithVersion(@Param("id") Long id, @Param("amount")BigDecimal amount, @Param("version")Integer version);

    /**
     * 扣减余额（乐观锁 + 余额充足双重条件），返回0表示并发冲突或余额不足
     */
    int deductBalanceWithVersion(@Param("id") Long id, @Param("amount")BigDecimal amount, @Param("version")Integer version);
}