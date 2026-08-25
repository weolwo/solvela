package solvela.mall.address.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.mall.address.domain.entity.MallAddress;

/**
 * 商城-会员收货地址簿 Dao
 *
 * <p><b>没有后台查询接口</b>：地址簿是会员自己的东西，后台把全量收件人姓名、电话、
 * 详细地址铺成一个列表，截个图就是一次批量泄露 —— 而运营在这个列表上没有任何要做的事
 * （发货看的是 t_physical_delivery 里的收件快照，不是地址簿）。
 * 生成器留的 controller / service / VO 已删除，只保留数据层供履约链路按 id 取。
 *
 * <p>🔴 <b>遗留待办</b>：mall.sql 明确要求这张表的收件三列走与 t_physical_delivery
 * 同一套 PiiCipher + PiiTypeHandler（同一个 handler、同一把密钥），
 * 但实体上目前还没挂 —— C 端写入链路落地前必须补上，否则会明文入库。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:11
 * @Copyright weolwo
 */
@Mapper
public interface MallAddressDao extends BaseMapper<MallAddress> {
}
