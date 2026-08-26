package solvela.ledger.logistic.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.domain.dto.PhysicalDeliveryDTO;
import solvela.ledger.logistic.domain.query.PhysicalDeliveryQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import java.util.List;

/**
 * 发货物流表 Dao
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@Mapper
public interface PhysicalDeliveryDao extends BaseMapper<PhysicalDelivery> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PhysicalDeliveryDTO> queryPage(Page<?> page, @Param("queryForm") PhysicalDeliveryQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<PhysicalDeliveryDTO> queryList(@Param("queryForm") PhysicalDeliveryQuery queryForm);

    // ==================== 统计面板 ====================

    /**
     * 本期新增的履约单（时间窗落在 {@code create_time}）
     */
    java.util.Map<String, Object> selectNewStat(@Param("form") LedgerStatQuery form);

    /**
     * 履约状态与积压体检。<b>刻意不带时间窗，统计的是全量</b>：
     * 积压是存量，限制在今天会让压了三天的单子正好从页面上消失，
     * 而那恰恰是这个页面唯一需要有人动手的东西。
     *
     * <p>待发货拆成「收件信息没补全（想发也发不了）」和「地址齐了等发货」两类 ——
     * 光看一个总数分不出这两种，运营只能一单一单点开看。
     */
    java.util.Map<String, Object> selectStatusStat();

    /**
     * 来源维度分布（全量）
     */
    List<java.util.Map<String, Object>> selectSourceStat();

    // ----- 物理删除 -----

    /**
     * 单个物理删除
     */
    long discardById(@Param("id") Long id);

    /**
     * 批量物理删除
     */
    void discardBatchIds(@Param("idList") List<Long> idList);
}