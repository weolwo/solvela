package solvela.admin.module.mall.commodity.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.domain.ValidateList;
import solvela.base.web.CurrentUser;
import solvela.mall.commodity.domain.form.MallCommodityQueryForm;
import solvela.mall.commodity.domain.form.MallCommoditySaveForm;
import solvela.mall.commodity.domain.vo.MallCommodityDetailVO;
import solvela.mall.commodity.domain.vo.MallCommodityVO;
import solvela.mall.commodity.service.MallCommodityService;

import java.util.List;

/**
 * 商城-商品 Controller
 *
 * <p><b>写操作只有 save 一个口子</b>，生成器留下的 add / update 已删除。
 * 商品是主子表（主表 + N 个 SKU + 轮播图引用），拆成多个接口的话，
 * 「主表存进去了、第 3 个 SKU 失败」会留下一个半截商品，而前端没有能力回滚已成功的那几个请求。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-商品")
@RequestMapping("/mallCommodity")
public class MallCommodityController {

    private final MallCommodityService mallCommodityService;

    @Operation(summary = "分页查询 @author weolwo")
    @PostMapping("/queryPage")
    @SaCheckPermission("mallCommodity:query")
    public ResponseDTO<PageResult<MallCommodityVO>> queryPage(@RequestBody @Valid MallCommodityQueryForm queryForm) {
        return ResponseDTO.ok(mallCommodityService.queryPage(queryForm));
    }

    @Operation(summary = "商品详情：主表+SKU+轮播图，编辑页回显用 @author weolwo")
    @GetMapping("/detail/{id}")
    @SaCheckPermission("mallCommodity:query")
    public ResponseDTO<MallCommodityDetailVO> detail(@PathVariable Long id) {
        return mallCommodityService.detail(id);
    }

    /**
     * 「生成」按钮：给运营一个可用的候选编码。运营也可以自己手输，两条路径都由 save 判重。
     * <b>不是预占</b> —— 中间隔着填表单的几分钟，那期间它可能被别人占走。
     */
    @Operation(summary = "生成商品编码（候选值，保存时判重） @author weolwo")
    @GetMapping("/generateCode")
    @SaCheckPermission("mallCommodity:add")
    public ResponseDTO<String> generateCode() {
        return mallCommodityService.generateCommodityCode();
    }

    /**
     * 生成 N 个 SKU 编码，供「生成/刷新 SKU 表」一次取走。同样只是候选值，判重在保存时做。
     */
    @Operation(summary = "批量生成SKU编码（仅展示，保存时判重） @author weolwo")
    @GetMapping("/generateSkuCodes/{count}")
    @SaCheckPermission("mallCommodity:update")
    public ResponseDTO<List<String>> generateSkuCodes(@PathVariable Integer count) {
        return mallCommodityService.generateSkuCodes(count == null ? 0 : count);
    }

    /**
     * 聚合保存：{@code form.id} 为空即新建。
     *
     * <p>权限点故意只挂 update：新建与编辑在业务上是同一个动作（配一个商品），
     * 拆成两个权限会出现「能新建不能改」这种没人想要的授权组合。
     */
    @Operation(summary = "保存商品（含SKU、轮播图），id为空即新建 @author weolwo")
    @PostMapping("/save")
    @SaCheckPermission("mallCommodity:update")
    public ResponseDTO<Long> save(@RequestBody @Valid MallCommoditySaveForm saveForm) {
        return mallCommodityService.save(saveForm, CurrentUser.orNull());
    }

    @Operation(summary = "上架/下架 @author weolwo")
    @GetMapping("/updateStatus/{id}/{status}")
    @SaCheckPermission("mallCommodity:update")
    public ResponseDTO<String> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        return mallCommodityService.updateStatus(id, status, CurrentUser.orNull());
    }

    @Operation(summary = "批量删除 @author weolwo")
    @PostMapping("/batchDelete")
    @SaCheckPermission("mallCommodity:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return mallCommodityService.batchDelete(idList);
    }

    @Operation(summary = "单个删除 @author weolwo")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("mallCommodity:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return mallCommodityService.delete(id);
    }
}
