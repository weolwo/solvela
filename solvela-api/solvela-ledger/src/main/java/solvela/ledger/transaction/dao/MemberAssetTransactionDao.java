package solvela.ledger.transaction.dao;

import java.util.List;

import solvela.ledger.MemberAssetTransaction;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionDTO;
import solvela.ledger.transaction.domain.query.MemberAssetTransactionQuery;
import solvela.ledger.stat.domain.query.LedgerStatQuery;
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
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberAssetTransactionDTO> queryPage(Page<?> page, @Param("queryForm") MemberAssetTransactionQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberAssetTransactionDTO> queryList(@Param("queryForm") MemberAssetTransactionQuery queryForm);

    // ==================== 统计面板 ====================

    /**
     * 时间范围内的收支汇总（按资产类型分组）+ 体检计数，一次扫表算完。
     *
     * <p>金额必须按 {@code asset_type} 分组：积分和现金不是同一个量纲。
     * 方向只看 {@code transaction_type} —— {@code change_amount} 存的是绝对值。
     *
     * <p>钱包页面的「今日变动」直接复用这一条，不另写一份 SQL：
     * 同一个口径出现两处实现，早晚会漂。
     */
    List<java.util.Map<String, Object>> selectAssetFlowStat(@Param("form") LedgerStatQuery form);

    /**
     * 时间范围内的总计与体检计数（笔数、会员数、人工调账、余额为负…）。
     */
    java.util.Map<String, Object> selectStat(@Param("form") LedgerStatQuery form);

    /**
     * 业务类型分布（TOP 10）。只给笔数 —— 同一 biz_type 下可能混着积分和现金，
     * 给金额就得再拆一层资产类型，页面上宽到没法看。
     */
    List<java.util.Map<String, Object>> selectBizTypeStat(@Param("form") LedgerStatQuery form);

    /*
     * 原先这里有 deleteById / batchDelete 两个<b>物理删除</b>，已随写接口一起移除（v3.69.0）。
     * 账务与审计流水删掉就再也查不回来，事后连"少了什么"都不知道。
     */
}