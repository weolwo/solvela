package solvela.admin.module.ledger.coupontemplate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.coupon.CouponTemplate;
import solvela.ledger.coupon.template.service.CouponTemplateService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 优惠券模板 Controller。
 *
 * <h3>🔴 没有「修改」，只有「新增版本」；没有「删除」，只有「停用」</h3>
 * 两个都不是漏了，而且服务端也没有对应方法：
 *
 * <ul>
 *   <li><b>不能原地改</b>：用户手里那张「满100减20」，运营改成「满200减20」之后，
 *       如果核销读的是模板当前值，用户手里的券就贬值了 —— 那是资损与信任问题。
 *       所以发券时会把规则<b>快照</b>进 {@code t_member_coupon}，核销根本不读模板；</li>
 *   <li><b>不能删</b>：删掉一版，运营就再也回答不了「用户手里这张券当时是什么规则」，
 *       而券的纠纷恰恰总是要回答这个。</li>
 * </ul>
 *
 * <p>页面上的「编辑」点开的也是「基于当前版本新建下一版」。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "优惠券模板")
@RequestMapping("/couponTemplate")
public class CouponTemplateController {

    private final CouponTemplateService couponTemplateService;

    @Operation(summary = "模板列表（每个编码取最新启用版）")
    @GetMapping("/list")
    @RequiresPermission("couponTemplate:query")
    public List<CouponTemplate> list() {
        return couponTemplateService.listLatest();
    }

    @Operation(summary = "某个编码的全部历史版本，新的在前")
    @GetMapping("/versions")
    @RequiresPermission("couponTemplate:query")
    public List<CouponTemplate> versions(@RequestParam String couponCode) {
        return couponTemplateService.listVersions(couponCode);
    }

    /**
     * 新增一个版本。
     *
     * <p>⚠️ 前端要把「这会新增一版、老版本仍在为已发出去的券服务」说清楚，
     * 否则运营会以为自己在改一个东西，然后疑惑版本号为什么一直涨。
     */
    @Operation(summary = "新增版本（编辑 = 新增下一版，不是原地改）")
    @PostMapping("/save")
    @RequiresPermission("couponTemplate:save")
    public int save(@RequestBody @Valid CouponTemplate template) {
        template.setCreateBy(CurrentEmployee.nameOrNull());
        return couponTemplateService.save(template);
    }

    @Operation(summary = "停用某一版（不删除 —— 删了就查不到历史券当时的规则）")
    @PostMapping("/disable")
    @RequiresPermission("couponTemplate:save")
    public void disable(@RequestParam String couponCode, @RequestParam Integer version) {
        couponTemplateService.disable(couponCode, version);
    }
}
