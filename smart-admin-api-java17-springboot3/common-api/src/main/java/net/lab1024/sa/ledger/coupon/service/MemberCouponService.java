package net.lab1024.sa.ledger.coupon.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.ledger.coupon.dao.MemberCouponDao;
import net.lab1024.sa.ledger.coupon.domain.entity.MemberCoupon;
import net.lab1024.sa.ledger.coupon.domain.form.MemberCouponAddForm;
import net.lab1024.sa.ledger.coupon.domain.form.MemberCouponQueryForm;
import net.lab1024.sa.ledger.coupon.domain.form.MemberCouponUpdateForm;
import net.lab1024.sa.ledger.coupon.domain.vo.MemberCouponVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 会员优惠券 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberCouponService {

    private final MemberCouponDao memberCouponDao;

    /**
     * 分页查询
     */
    public PageResult<MemberCouponVO> queryPage(MemberCouponQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberCouponVO> list = memberCouponDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MemberCouponAddForm addForm) {
        MemberCoupon memberCoupon = SmartBeanUtil.copy(addForm, MemberCoupon.class);
        memberCouponDao.insert(memberCoupon);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MemberCouponUpdateForm updateForm) {
        MemberCoupon memberCoupon = SmartBeanUtil.copy(updateForm, MemberCoupon.class);
        memberCouponDao.updateById(memberCoupon);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        memberCouponDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        memberCouponDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
