package solvela.admin.module.ledger.coupon.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.ledger.coupon.domain.form.MemberCouponQueryForm;
import solvela.ledger.coupon.domain.vo.MemberCouponStatVO;
import solvela.ledger.coupon.domain.vo.MemberCouponVO;
import solvela.ledger.coupon.service.MemberCouponService;
import solvela.ledger.stat.domain.form.LedgerStatForm;

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

    /*
     * 生成器给的「添加 / 更新 / 批量删除 / 单个删除」四个接口已整组移除（v3.69.0）。
     *
     * 这张表是账务流水，不是业务配置：
     *   · 改一行等于让账面和真实发生过的事脱节，而对账正是以它为准；
     *   · 原先的 delete 还是<b>物理删除</b>（mapper 里就是 DELETE FROM），删完不留痕，
     *     事后连"少了什么"都查不出来。
     *
     * 前端本来就没调用这几个接口（api 封装里只留了查询），但 HTTP 出口一直是活的 ——
     * 拿到 token 就能打。真需要人工订正，走提案链路或 DBA 流程并留痕。
     */
}
