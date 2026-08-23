package sa.mall.sku.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.mall.sku.domain.form.MallSkuQueryForm;
import sa.mall.sku.domain.vo.MallSkuVO;
import sa.mall.sku.service.MallSkuService;

/**
 * 商城-SKU与库存 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-SKU与库存")
@RequestMapping("/mallSku")
public class MallSkuController {

    private final MallSkuService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("mallSku:query")
    public ResponseDTO<PageResult<MallSkuVO>> queryPage(@RequestBody @Valid MallSkuQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }


}
