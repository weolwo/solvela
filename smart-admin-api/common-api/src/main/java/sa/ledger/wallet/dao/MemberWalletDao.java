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

    // ==================== 统计面板 ====================

    /**
     * 钱包总计与体检。<b>刻意不带时间范围，统计的是全量</b>：
     * 钱包表是存量表，只有当前余额、没有历史切片，「今天的总余额」这个说法本身不成立。
     *
     * <p>本页的「本期变动」走交易明细表，不在这里另写一份。
     */
    java.util.Map<String, Object> selectStat();

    /**
     * 按资产类型的余额存量。余额必须按类型分开 —— 积分和现金不是同一个量纲。
     */
    List<java.util.Map<String, Object>> selectAssetBalanceStat();


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

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */

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