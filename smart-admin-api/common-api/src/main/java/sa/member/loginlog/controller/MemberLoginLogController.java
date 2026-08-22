package sa.member.loginlog.controller;

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
import sa.member.domain.form.MemberLoginLogQueryForm;
import sa.member.loginlog.domain.vo.MemberLoginLogVO;
import sa.member.loginlog.service.MemberLoginLogService;

/**
 * 会员登录日志（append-only，按月分区） Controller
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员登录日志（append-only，按月分区）")
@RequestMapping("/memberLoginLog")
public class MemberLoginLogController {

    private final MemberLoginLogService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<MemberLoginLogVO>> queryPage(@RequestBody @Valid MemberLoginLogQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }


}
