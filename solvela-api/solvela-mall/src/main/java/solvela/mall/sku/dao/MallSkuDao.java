package solvela.mall.sku.dao;

        import java.util.List;

        import solvela.mall.MallSku;
        import solvela.mall.sku.domain.query.MallSkuQuery;
        import solvela.mall.sku.domain.dto.MallSkuDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商城-SKU与库存 Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@Mapper
public interface MallSkuDao extends BaseMapper<MallSku> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    /**
     * 卖出：<b>一条原子 UPDATE 就防住超卖</b>，affected rows = 0 即失败。
     *
     * <p>🔴 <b>不要改成「查出来 → 判一下 → 写回去」</b>，也不要加乐观锁 version：
     * DDL 里删掉 version 的理由写得很清楚 —— 条件扣减本身已经防住超卖了，
     * version 只服务于「读-改-写」那种写法，在这里纯属让高并发白白 CAS 失败重试。
     *
     * <p>用于<b>纯积分</b>（payType=1）：同步扣分，不存在悬挂，
     * 所以直接从可用库存转成已售，跳过 locked_stock 那一步。
     *
     * @return 影响行数。0 = 库存不够或 SKU 已停用
     */
    @Update("""
            UPDATE t_mall_sku
               SET sold_count = sold_count + #{quantity}
             WHERE id = #{skuId}
               AND sku_status = 1
               AND total_stock - locked_stock - sold_count >= #{quantity}
            """)
    int sell(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /**
     * 锁定：用于<b>积分+现金</b>（payType=2）—— 要等第三方支付回调，中间悬着。
     * 到期由超时释放 job 调 {@link #releaseLocked} 放回去。
     *
     * @return 影响行数。0 = 库存不够或 SKU 已停用
     */
    @Update("""
            UPDATE t_mall_sku
               SET locked_stock = locked_stock + #{quantity}
             WHERE id = #{skuId}
               AND sku_status = 1
               AND total_stock - locked_stock - sold_count >= #{quantity}
            """)
    int lock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    /** 取消 / 超时释放：把锁定的放回去。{@code locked_stock >= qty} 防止减成负数 */
    @Update("""
            UPDATE t_mall_sku
               SET locked_stock = locked_stock - #{quantity}
             WHERE id = #{skuId}
               AND locked_stock >= #{quantity}
            """)
    int releaseLocked(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    List<MallSkuDTO> queryPage(Page<?> page, @Param("queryForm") MallSkuQuery queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MallSkuDTO> queryList(@Param("queryForm") MallSkuQuery queryForm);

}