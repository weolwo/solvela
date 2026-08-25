package sa.admin.module.member.controller;

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
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartRequestUtil;
import sa.member.domain.form.MemberQueryForm;
import sa.member.domain.form.MemberRemarkForm;
import sa.member.domain.vo.MemberVO;
import sa.member.service.MemberService;

/**
 * 会员主表 Controller
 *
 * <p><b>没有新增接口</b>：会员是 C 端自己注册出来的，后台凭空造一个会员会绕过注册链路的
 * 手机号校验、发号器、钱包初始化 —— 造出来的是一个数据不全、登录不了的壳。
 * 生成器留下的 MemberAddForm / MemberUpdateForm 没有对应的接口，就是这个原因。
 *
 * <p>后台能改的只有两样：<b>状态</b>（冻结/解冻，风控动作）和<b>运营备注</b>。
 * 各开一个窄接口，而不是一个能改整行的大接口。
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

    private final MemberService memberService;

    @Operation(summary = "分页查询 @author weolwo")
    @PostMapping("/queryPage")
    @SaCheckPermission("member:query")
    public ResponseDTO<PageResult<MemberVO>> queryPage(@RequestBody @Valid MemberQueryForm queryForm) {
        return ResponseDTO.ok(memberService.queryPage(queryForm));
    }

    /**
     * 冻结 / 解冻。已注销是终态，服务端会拒绝把它改回来。
     */
    @Operation(summary = "冻结/解冻 @author weolwo")
    @GetMapping("/updateStatus/{memberId}/{status}")
    @SaCheckPermission("member:update")
    public ResponseDTO<String> updateStatus(@PathVariable Long memberId, @PathVariable Integer status) {
        return memberService.updateStatus(memberId, status, SmartRequestUtil.getRequestUser());
    }

    /**
     * 保存运营备注（列表里点一下就能改的那个）。
     *
     * <p>用 POST + body 而不是把备注拼在 URL 上：备注是自由文本，可能有斜杠、井号、中文，
     * 放进路径参数要处理一堆转义，而且会被完整记进 access log。
     */
    @Operation(summary = "保存运营备注 @author weolwo")
    @PostMapping("/updateRemark")
    @SaCheckPermission("member:update")
    public ResponseDTO<String> updateRemark(@RequestBody @Valid MemberRemarkForm remarkForm) {
        return memberService.updateRemark(remarkForm.getMemberId(), remarkForm.getRemark(),
                SmartRequestUtil.getRequestUser());
    }
}
