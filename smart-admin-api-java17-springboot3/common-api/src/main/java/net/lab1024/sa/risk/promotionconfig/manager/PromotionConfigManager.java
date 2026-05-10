package net.lab1024.sa.risk.promotionconfig.manager;

import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.promotionconfig.dao.PromotionConfigDao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 优惠配置表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PromotionConfigManager extends ServiceImpl<PromotionConfigDao, PromotionConfig> {


}
