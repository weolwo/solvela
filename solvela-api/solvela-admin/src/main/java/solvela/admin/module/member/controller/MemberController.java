package solvela.admin.module.member.controller;

import solvela.enums.MemberStatusEnum;
import solvela.web.RequiresPermission;
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
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.member.domain.form.MemberQueryForm;
import solvela.member.domain.query.MemberQuery;
import solvela.admin.module.member.domain.form.MemberRemarkForm;
import solvela.admin.module.member.domain.vo.MemberVO;
import solvela.member.domain.dto.MemberDTO;
import solvela.member.service.MemberService;

/**
 * 会员主表 Controller
 *
 * <p><b>没有新增接口</b>：会员是 C 端自己注册出来的，后台凭空造一个会员会绕过注册链路的
 * 手机号校验、发号器、钱包初始化 —— 造出来的是一个数据不全、登录不了的壳。
 * 生成器曾留下一对 Add/Update 表单，因为没有也不该有对应接口，已随本轮清理删除。
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
    @RequiresPermission("member:query")
    public PageResult<MemberVO> queryPage(@RequestBody @Valid MemberQueryForm queryForm) {
        PageResult<MemberDTO> page = memberService.queryPage(SolvelaBeanUtil.copy(queryForm, MemberQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, MemberVO.class);
    }

    /**
     * 冻结 / 解冻。已注销是终态，服务端会拒绝把它改回来。
     */
    @Operation(summary = "冻结/解冻 @author weolwo")
    @GetMapping("/updateStatus/{memberId}/{status}")
    @RequiresPermission("member:update")
    public void updateStatus(@PathVariable Long memberId, @PathVariable MemberStatusEnum status) {
        memberService.updateStatus(memberId, status, CurrentEmployee.nameOrNull());
    }

    /**
     * 保存运营备注（列表里点一下就能改的那个）。
     *
     * <p>用 POST + body 而不是把备注拼在 URL 上：备注是自由文本，可能有斜杠、井号、中文，
     * 放进路径参数要处理一堆转义，而且会被完整记进 access log。
     */
    @Operation(summary = "保存运营备注 @author weolwo")
    @PostMapping("/updateRemark")
    @RequiresPermission("member:update")
    public void updateRemark(@RequestBody @Valid MemberRemarkForm remarkForm) {
        memberService.updateRemark(remarkForm.getMemberId(), remarkForm.getRemark(),
                CurrentEmployee.nameOrNull());
    }
}
