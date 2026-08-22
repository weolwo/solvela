package sa.member.verify.controller;

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
import sa.member.verify.domain.form.MemberVerifyQueryForm;
import sa.member.verify.domain.vo.MemberVerifyVO;
import sa.member.verify.service.MemberVerifyService;

/**
 * 会员实名信息（敏感，与主表分离） Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员实名信息（敏感，与主表分离）")
@RequestMapping("/memberVerify")
public class MemberVerifyController {

    private final MemberVerifyService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MemberVerifyVO>> queryPage(@RequestBody @Valid MemberVerifyQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }


}
