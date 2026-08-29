package solvela.admin.module.member.loginlog.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.web.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.admin.module.member.loginlog.domain.form.MemberLoginLogQueryForm;
import solvela.member.loginlog.domain.query.MemberLoginLogQuery;
import solvela.member.loginlog.domain.dto.MemberLoginLogStatDTO;
import solvela.admin.module.member.loginlog.domain.vo.MemberLoginLogVO;
import solvela.member.loginlog.domain.dto.MemberLoginLogDTO;
import solvela.member.loginlog.service.MemberLoginLogService;

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

    @Operation(summary = "分页查询 @author weolwo")
    @PostMapping("/queryPage")
    @RequiresPermission("memberLoginLog:query")
    public ResponseDTO<PageResult<MemberLoginLogVO>> queryPage(@RequestBody @Valid MemberLoginLogQueryForm queryForm) {
        PageResult<MemberLoginLogDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, MemberLoginLogQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, MemberLoginLogVO.class));
    }



    /**
     * 统计。与分页查询收同一个表单 —— 顶部筛选改了统计跟着变，看的是同一批数据。
     * 默认（不传任何条件）由前端传当天的起止日期。
     */
    @Operation(summary = "登录统计：与列表同一套筛选条件 @author weolwo")
    @PostMapping("/queryStat")
    @RequiresPermission("memberLoginLog:query")
    public ResponseDTO<MemberLoginLogStatDTO> queryStat(@RequestBody @Valid MemberLoginLogQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryStat(SolvelaBeanUtil.copy(queryForm, MemberLoginLogQuery.class)));
    }
}
