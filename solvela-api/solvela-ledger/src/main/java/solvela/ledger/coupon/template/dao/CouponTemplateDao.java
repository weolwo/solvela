package solvela.ledger.coupon.template.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.coupon.CouponTemplate;

import java.util.List;

/**
 * 优惠券模板 Dao。
 *
 * <h3>🔴 复合主键 (coupon_code, version)，按主键的操作必须在这里显式写</h3>
 * {@link CouponTemplate} 上没有也不该有 {@code @TableId} ——
 * MyBatis-Plus 的单主键注解表达不了复合主键。于是基类的
 * {@code selectById} / {@code updateById} / {@code deleteById}
 * 对本实体<b>根本生成不出来</b>，调了会在运行期抛
 * {@code Invalid bound statement (not found)}，而<b>编译期一点迹象都没有</b>。
 *
 * <p>这个坑 2026-09-15 在 {@code NotificationTemplateDao} 上踩过一次
 *（是真库起服务调接口才炸出来的），这里不再踩第二次。
 * 本域的 Dao 方法绑定由 {@code CouponMapperBindingTest} 逐个点名。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Mapper
public interface CouponTemplateDao extends BaseMapper<CouponTemplate> {

    /**
     * 取某个券模<b>当前启用的最新版本</b>。发券时用它决定「这次按哪一版发」。
     *
     * <p>取到之后规则就被快照进 {@code t_member_coupon} 了，
     * 之后模板再改版也不影响已经发出去的券。
     *
     * @return 没有启用版本时返回 null —— 调用方必须处理，不要假设模板一定在
     */
    CouponTemplate selectLatestEnabled(@Param("couponCode") String couponCode);

    /**
     * 按 (code, version) 精确取一版。
     *
     * <p>🔴 <b>不过滤 status</b>：模板停用之后，运营仍然要能回答
     * 「用户手里这张券当时是什么规则」。停用的意思是「以后不再用它发新券」，
     * 不是「过去发的都查不到了」。
     */
    CouponTemplate selectByCodeAndVersion(@Param("couponCode") String couponCode,
                                          @Param("version") Integer version);

    /** 某个编码的全部版本，新的在前。运营看改版历史 */
    List<CouponTemplate> selectVersions(@Param("couponCode") String couponCode);

    /** 全部启用中的模板，按 (code, version) 升序。列表页与缓存预热用 */
    List<CouponTemplate> selectAllEnabled();

    /**
     * 某个编码的当前最大版本号。新增版本时用它 +1。
     *
     * <p>🔴 <b>不过滤 status</b>：停用过的版本也占着号。过滤掉的话，
     * 停用 v2 之后新增会又拿到 2，撞主键 —— 而报出来的是个
     * {@code DuplicateKeyException}，根因一点都不明显。
     *
     * @return 没有任何版本时返回 null
     */
    Integer selectMaxVersion(@Param("couponCode") String couponCode);

    /** 停用某一版。🔴 没有删除 —— 删了就查不到历史券当时的规则 */
    int disableVersion(@Param("couponCode") String couponCode, @Param("version") Integer version);
}
