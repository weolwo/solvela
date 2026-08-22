package sa.mall.order.manager;

import sa.mall.order.dao.MallOrderDao;
import sa.mall.order.domain.entity.MallOrder;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-兑换订单  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallOrderManager extends ServiceImpl<MallOrderDao, MallOrder> {


}
