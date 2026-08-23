package sa.mall.order.controller;

import sa.mall.order.domain.entity.MallOrder;
import sa.mall.order.domain.form.MallOrderAddForm;
import sa.mall.order.domain.form.MallOrderQueryForm;
import sa.mall.order.domain.form.MallOrderUpdateForm;
import sa.mall.order.domain.vo.MallOrderVO;
import sa.mall.order.service.MallOrderService;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 商城-兑换订单 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-兑换订单")
@RequestMapping("/mallOrder")
public class MallOrderController {

    private final MallOrderService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("mallOrder:query")
    public ResponseDTO<PageResult<MallOrderVO>> queryPage(@RequestBody @Valid MallOrderQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("mallOrder:add")
    public ResponseDTO<String> add(@RequestBody @Valid MallOrderAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("mallOrder:update")
    public ResponseDTO<String> update(@RequestBody @Valid MallOrderUpdateForm updateForm) {
        return Service.update(updateForm);
    }

}
