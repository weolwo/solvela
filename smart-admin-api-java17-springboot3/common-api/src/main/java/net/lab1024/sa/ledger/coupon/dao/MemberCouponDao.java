package net.lab1024.sa.ledger.coupon.dao;

        import java.util.List;
        import net.lab1024.sa.ledger.coupon.domain.entity.MemberCoupon;
        import net.lab1024.sa.ledger.coupon.domain.form.MemberCouponQueryForm;
        import net.lab1024.sa.ledger.coupon.domain.vo.MemberCouponVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 会员优惠券 Dao
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@Mapper
public interface MemberCouponDao extends BaseMapper<MemberCoupon> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponVO> queryPage(Page<?> page, @Param("queryForm") MemberCouponQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberCouponVO> queryList(@Param("queryForm") MemberCouponQueryForm queryForm);

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