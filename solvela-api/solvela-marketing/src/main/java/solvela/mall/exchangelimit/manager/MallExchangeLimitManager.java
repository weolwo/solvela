package solvela.mall.exchangelimit.manager;

import solvela.mall.exchangelimit.dao.MallExchangeLimitDao;
import solvela.mall.exchangelimit.domain.entity.MallExchangeLimit;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-会员限兑计数  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallExchangeLimitManager extends ServiceImpl<MallExchangeLimitDao, MallExchangeLimit> {


}
