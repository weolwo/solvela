package solvela.mall.address.manager;

import solvela.mall.address.domain.entity.MallAddress;
import solvela.mall.address.dao.MallAddressDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-会员收货地址簿  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallAddressManager extends ServiceImpl<MallAddressDao, MallAddress> {


}
