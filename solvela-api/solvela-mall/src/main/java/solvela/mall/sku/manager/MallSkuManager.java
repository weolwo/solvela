package solvela.mall.sku.manager;

import solvela.mall.sku.dao.MallSkuDao;
import solvela.mall.MallSku;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 商城-SKU与库存  Manager
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MallSkuManager extends ServiceImpl<MallSkuDao, MallSku> {


}
