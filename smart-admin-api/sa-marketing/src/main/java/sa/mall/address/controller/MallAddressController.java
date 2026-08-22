package sa.mall.address.controller;

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
import sa.mall.address.domain.form.MallAddressAddForm;
import sa.mall.address.domain.form.MallAddressQueryForm;
import sa.mall.address.domain.form.MallAddressUpdateForm;
import sa.mall.address.domain.vo.MallAddressVO;
import sa.mall.address.service.MallAddressService;

/**
 * 商城-会员收货地址簿 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "商城-会员收货地址簿")
@RequestMapping("/mallAddress")
public class MallAddressController {

    private final MallAddressService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MallAddressVO>> queryPage(@RequestBody @Valid MallAddressQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid MallAddressAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid MallAddressUpdateForm updateForm) {
        return Service.update(updateForm);
    }

}
