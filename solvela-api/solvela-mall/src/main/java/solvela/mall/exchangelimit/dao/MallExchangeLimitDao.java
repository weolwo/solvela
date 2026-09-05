package solvela.mall.exchangelimit.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import solvela.mall.MallExchangeLimit;

/**
 * 商城-会员限兑计数 Dao
 *
 * <p><b>没有后台页面</b>：这张表是<b>运行态计数器</b>，由下单链路
 * 「INSERT ... ON DUPLICATE KEY UPDATE + 判 affected rows」维护 ——
 * 那个语句本身就是限兑的正确性来源（DDL 里写明了：不是 count 订单表，
 * 因为并发下两个请求会同时读到 count=0 而双双通过）。
 *
 * <p>把它做成一个可增删改的后台列表是危险的：手工改一行 used_count，
 * 等于凭空给某个会员发了一次或收走一次兑换资格，而且没有任何痕迹。
 * 逐行浏览它也没有意义 —— 运营看不出「member 1000000001 在 202608 用了 1 次」意味着什么。
 *
 * <p>唯一真实的使用场景是客服排查「我明明没兑过为什么说我超限了」，
 * 那是<b>按会员 + 商品点查</b>，将来并进会员详情或订单详情即可，不需要独立菜单。
 * 生成器留的 controller / service / VO 已删除，只保留数据层。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:36:47
 * @Copyright weolwo
 */
@Mapper
public interface MallExchangeLimitDao extends BaseMapper<MallExchangeLimit> {

    /**
     * 占用一次兑换额度。<b>这条语句本身就是限兑的正确性来源。</b>
     *
     * <h3>为什么不 count 订单表</h3>
     * {@code select count(*) ... where member_id=? and commodity_id=?} 在并发下有竞态：
     * 两个请求同时读到 count=0，双双通过校验。限兑 1 件的爆款商品，这个洞会被刷。
     *
     * <h3>为什么不用分布式锁</h3>
     * Redisson 锁能挡住，但锁的粒度是 member+commodity，热门商品下所有用户的请求
     * 会在同一批 key 上排队；且锁超时 / Redis 抖动时校验直接裸奔。
     * 唯一索引 + 条件 UPDATE 是数据库自己保证的，没有这些问题。
     *
     * <h3>🔴 period_key 由数据库时钟算，不是 JVM 时钟</h3>
     * 铁律 9/10。用 JVM 时间的话，跨时区部署时日切点对不上 ——
     * 用户在 00:00~08:00 之间能多兑一次，而这种 bug 只在特定时段出现。
     * 取值口径对齐 {@code t_task_record.period_key}：
     * LIFETIME → NONE；DAILY → 20260819；WEEKLY → 2026W34；MONTHLY → 202608。
     *
     * <h3>怎么判成功</h3>
     * MySQL 对「值没变的 UPDATE」返回 0 影响行数 —— 那正好是超限信号：
     * {@code IF()} 在会超限时把 used_count 原样写回去，于是 affected rows = 0。
     * <p>⚠️ 插入成功时 MySQL 返回 1，ON DUPLICATE KEY 更新成功时返回 <b>2</b>，
     * 所以判据是 {@code > 0}，不是 {@code == 1}。
     *
     * @param limitCount 周期内上限。调用方保证 &gt; 0（0 表示不限制，那时根本不该调本方法）
     * @return 影响行数，{@code > 0} 表示占用成功
     */
    @Insert("""
            INSERT INTO t_mall_exchange_limit (member_id, commodity_id, period_key, used_count)
            VALUES (#{memberId}, #{commodityId},
                    CASE #{limitPeriod}
                        WHEN 'DAILY'   THEN DATE_FORMAT(NOW(), '%Y%m%d')
                        WHEN 'WEEKLY'  THEN CONCAT(DATE_FORMAT(NOW(), '%x'), 'W', DATE_FORMAT(NOW(), '%v'))
                        WHEN 'MONTHLY' THEN DATE_FORMAT(NOW(), '%Y%m')
                        ELSE 'NONE'
                    END,
                    #{quantity})
            ON DUPLICATE KEY UPDATE
                used_count = IF(used_count + #{quantity} <= #{limitCount},
                                used_count + #{quantity},
                                used_count)
            """)
    int tryConsume(@Param("memberId") Long memberId,
                   @Param("commodityId") Long commodityId,
                   @Param("limitPeriod") String limitPeriod,
                   @Param("quantity") int quantity,
                   @Param("limitCount") int limitCount);

    /**
     * 归还额度（取消 / 退款 / 履约失败）。
     *
     * <p>{@code used_count >= quantity} 防止减成负数 —— 减成负数的表现是
     * 「这个用户可以无限兑」，而且没有任何报错。
     *
     * <p>⚠️ period_key 同样由数据库时钟算：<b>如果归还发生在跨周期之后</b>
     *（比如昨天兑的今天取消），这条会更新到<b>今天</b>那一行、甚至插不中任何行。
     * 这是已知的取舍：按订单上记的 period_key 归还才是严格正确的，
     * 但那需要订单表存一列 period_key —— 目前的取消都发生在同一周期内
     *（超时释放是分钟级的），所以先不加那一列。真出现跨周期取消再说。
     */
    @Update("""
            UPDATE t_mall_exchange_limit
               SET used_count = used_count - #{quantity}
             WHERE member_id = #{memberId}
               AND commodity_id = #{commodityId}
               AND period_key = CASE #{limitPeriod}
                        WHEN 'DAILY'   THEN DATE_FORMAT(NOW(), '%Y%m%d')
                        WHEN 'WEEKLY'  THEN CONCAT(DATE_FORMAT(NOW(), '%x'), 'W', DATE_FORMAT(NOW(), '%v'))
                        WHEN 'MONTHLY' THEN DATE_FORMAT(NOW(), '%Y%m')
                        ELSE 'NONE'
                    END
               AND used_count >= #{quantity}
            """)
    int release(@Param("memberId") Long memberId,
                @Param("commodityId") Long commodityId,
                @Param("limitPeriod") String limitPeriod,
                @Param("quantity") int quantity);
}
