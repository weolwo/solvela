package net.lab1024.sa.ledger.logistic.manager;

import net.lab1024.sa.ledger.logistic.domain.entity.PhysicalDelivery;
import net.lab1024.sa.ledger.logistic.dao.PhysicalDeliveryDao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 发货物流表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PhysicalDeliveryManager extends ServiceImpl<PhysicalDeliveryDao, PhysicalDelivery> {


}
