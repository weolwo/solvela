package solvela.mall.exchangelimit.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
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
}
