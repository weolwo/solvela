package sa.mall.order.controller;

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
import sa.mall.order.domain.form.MallOrderQueryForm;
import sa.mall.order.domain.vo.MallOrderStatVO;
import sa.mall.order.domain.vo.MallOrderVO;
import sa.mall.order.service.MallOrderService;

/**
 * 商城-兑换订单 Controller
 *
 * <p><b>只读</b>。订单由 C 端下单链路创建、由履约链路推进状态 ——
 * 后台凭空插一条会绕过扣积分、锁库存、限兑计数三件事，造出来的是一个账对不上的孤单；
 * 手工改状态更危险，状态机是履约链路的，拨一下不会真的去发货。
 * 生成器留的 add / update 已删除。
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

    private final MallOrderService mallOrderService;

    @Operation(summary = "分页查询 @author weolwo")
    @PostMapping("/queryPage")
    @SaCheckPermission("mallOrder:query")
    public ResponseDTO<PageResult<MallOrderVO>> queryPage(@RequestBody @Valid MallOrderQueryForm queryForm) {
        return ResponseDTO.ok(mallOrderService.queryPage(queryForm));
    }

    /**
     * 统计 + 兑换商品排行。收的是<b>和列表同一个表单</b> —— 顶部筛选改了统计跟着变。
     */
    @Operation(summary = "订单统计与兑换商品排行 @author weolwo")
    @PostMapping("/queryStat")
    @SaCheckPermission("mallOrder:query")
    public ResponseDTO<MallOrderStatVO> queryStat(@RequestBody @Valid MallOrderQueryForm queryForm) {
        return mallOrderService.queryStat(queryForm);
    }
}
