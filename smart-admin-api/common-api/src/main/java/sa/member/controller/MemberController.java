package sa.member.controller;

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
import sa.member.domain.form.MemberQueryForm;
import sa.member.domain.vo.MemberVO;
import sa.member.service.MemberService;

/**
 * 会员主表 Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 19:39:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员主表")
@RequestMapping("/member")
public class MemberController {

    private final MemberService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MemberVO>> queryPage(@RequestBody @Valid MemberQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }
}
