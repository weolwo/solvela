package solvela.risk.promotiongroup.manager;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.risk.PromotionGroup;
import solvela.risk.promotiongroup.dao.PromotionGroupDao;

/**
 * 优惠配置分组 Manager
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@RequiredArgsConstructor
@Service
public class PromotionGroupManager extends ServiceImpl<PromotionGroupDao, PromotionGroup> {
}
