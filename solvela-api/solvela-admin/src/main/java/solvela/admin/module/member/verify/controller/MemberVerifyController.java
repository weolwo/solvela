package solvela.admin.module.member.verify.controller;

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
import solvela.base.web.CurrentUser;
import solvela.member.verify.domain.form.MemberVerifyQueryForm;
import solvela.admin.module.member.verify.domain.form.MemberVerifyRejectForm;
import solvela.member.verify.domain.vo.MemberVerifyDetailVO;
import solvela.member.verify.domain.vo.MemberVerifyVO;
import solvela.member.verify.service.MemberVerifyService;

/**
 * 会员实名信息（敏感，与主表分离） Controller
 *
 * <p>运营在这个页面只做一件事：<b>审核</b>（通过 / 驳回）。姓名和身份证是用户提交的，
 * 后台改它们没有任何合法用途 —— 所以没有编辑接口。
 *
 * <p>🔴 <b>列表与详情刻意分成两个接口</b>：列表下发脱敏值，详情下发明文。
 * 审核一条时必须看到完整证件号（核对真伪就是这个页面的用途），
 * 但那是「一次看一条」的动作，和「列表一屏铺开几十条」是完全不同的暴露面。
 * 拆开之后，「谁能看到完整证件号」就变成了一个可以单独授权、单独审计的动作。
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员实名信息")
@RequestMapping("/memberVerify")
public class MemberVerifyController {

    private final MemberVerifyService memberVerifyService;

    @Operation(summary = "分页查询（姓名与身份证已脱敏） @author weolwo")
    @PostMapping("/queryPage")
    @SaCheckPermission("memberVerify:query")
    public ResponseDTO<PageResult<MemberVerifyVO>> queryPage(@RequestBody @Valid MemberVerifyQueryForm queryForm) {
        return ResponseDTO.ok(memberVerifyService.queryPage(queryForm));
    }

    /**
     * 单条详情，<b>返回明文</b>。权限点单独用 memberVerify:detail，
     * 这样「能看列表」和「能看完整证件号」可以分开授权。
     */
    @Operation(summary = "实名详情（明文，审核弹窗用） @author weolwo")
    @GetMapping("/detail/{id}")
    @SaCheckPermission("memberVerify:detail")
    public ResponseDTO<MemberVerifyDetailVO> detail(@PathVariable Long id) {
        return memberVerifyService.detail(id);
    }

    @Operation(summary = "审核通过 @author weolwo")
    @GetMapping("/approve/{id}")
    @SaCheckPermission("memberVerify:audit")
    public ResponseDTO<String> approve(@PathVariable Long id) {
        return memberVerifyService.approve(id, CurrentUser.orNull());
    }

    /**
     * 审核驳回。原因走 body 而不是路径参数：它是自由文本，会有中文和标点，
     * 放 URL 上要处理转义，而且会被完整记进 access log。
     */
    @Operation(summary = "审核驳回（必须填原因） @author weolwo")
    @PostMapping("/reject")
    @SaCheckPermission("memberVerify:audit")
    public ResponseDTO<String> reject(@RequestBody @Valid MemberVerifyRejectForm rejectForm) {
        return memberVerifyService.reject(rejectForm.getId(), rejectForm.getFailReason(),
                CurrentUser.orNull());
    }
}
