package solvela.ledger.coupon.manager;

import solvela.ledger.coupon.domain.entity.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 会员优惠券  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberCouponManager extends ServiceImpl<MemberCouponDao, MemberCoupon> {


}
