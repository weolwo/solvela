package sa.ledger.coupon.controller;

import sa.base.common.domain.ValidateList;
import sa.ledger.coupon.domain.entity.MemberCoupon;
import sa.ledger.coupon.domain.form.MemberCouponAddForm;
import sa.ledger.coupon.domain.form.MemberCouponQueryForm;
import sa.ledger.coupon.domain.form.MemberCouponUpdateForm;
import sa.ledger.coupon.domain.vo.MemberCouponStatVO;
import sa.ledger.coupon.domain.vo.MemberCouponVO;
import sa.ledger.stat.domain.form.LedgerStatForm;
import sa.ledger.coupon.service.MemberCouponService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 会员优惠券 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员优惠券")
@RequestMapping("/memberCoupon")
public class MemberCouponController {

    private final MemberCouponService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("memberCoupon:query")
    public ResponseDTO<PageResult<MemberCouponVO>> queryPage(@RequestBody @Valid MemberCouponQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "优惠券统计：本期发放与本期核销（两个口径）、券库存与过期体检（时间范围默认当天）")
    @PostMapping("/stat")
    @SaCheckPermission("memberCoupon:query")
    public ResponseDTO<MemberCouponStatVO> stat(@RequestBody @Valid LedgerStatForm form) {
        return ResponseDTO.ok(Service.stat(form));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("memberCoupon:add")
    public ResponseDTO<String> add(@RequestBody @Valid MemberCouponAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("memberCoupon:update")
    public ResponseDTO<String> update(@RequestBody @Valid MemberCouponUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("memberCoupon:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("memberCoupon:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
