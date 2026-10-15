package solvela.admin.module.member.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.member.risk.domain.form.MemberRiskEventQueryForm;
import solvela.admin.module.member.risk.domain.vo.MemberRiskEventDetailVO;
import solvela.admin.module.member.risk.domain.vo.MemberRiskEventVO;
import solvela.admin.module.member.risk.domain.vo.MemberRiskHitVO;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PiiHasher;
import solvela.member.risk.domain.dto.MemberRiskEventDTO;
import solvela.member.risk.domain.dto.MemberRiskEventDetailDTO;
import solvela.member.risk.domain.query.MemberRiskEventQuery;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;
import solvela.web.RequiresPermission;

import java.time.LocalDateTime;

/**
 * 登录风控 - 决策流水。
 *
 * <h3>它回答的是客服最常被问的那句话</h3>
 * 「我为什么要验证码 / 为什么登不上」—— 查到那一次尝试，点开看命中了哪几条规则、依据是什么。
 * 这个页面之前不存在：登录日志的 {@code member_id} 是 NOT NULL，<b>查无此人的尝试一行都不留</b>，
 * 而那恰恰是撞库最常见的样子。
 *
 * <h3>它也是「陪跑 → 生效」唯一的依据</h3>
 * 勾上「只看结论不一致」，看到的就是「把这些规则切到生效会多打扰的那些人」（方案 §8.2）。
 *
 * @Date 2026-10-16
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "登录风控 - 决策流水")
@RequestMapping("/memberRiskEvent")
public class MemberRiskEventController {

    private final MemberRiskAdminService riskAdminService;

    private final PiiHasher piiHasher;

    @Operation(summary = "分页查询。不传时间默认最近 7 天")
    @PostMapping("/queryPage")
    @RequiresPermission("memberRisk:query")
    public PageResult<MemberRiskEventVO> queryPage(@RequestBody @Valid MemberRiskEventQueryForm form) {
        MemberRiskEventQuery query = SolvelaBeanUtil.copy(form, MemberRiskEventQuery.class);
        query.setIdentityHashHex(hashIdentity(form.getIdentity()));
        PageResult<MemberRiskEventDTO> page = riskAdminService.queryEventPage(query);
        return SolvelaPageUtil.convert2PageResult(page, MemberRiskEventVO.class);
    }

    /**
     * 一次尝试的全貌。
     *
     * <p>🔴 {@code createTime} 必须跟着传：表按月分区，只按 id 查不裁剪分区
     * （列表里每一行都带着它，前端原样回传即可）。
     */
    @Operation(summary = "流水详情：结论 + 每条命中的规则")
    @PostMapping("/detail")
    @RequiresPermission("memberRisk:query")
    public MemberRiskEventDetailVO detail(@RequestBody @Valid MemberRiskEventDetailForm form) {
        MemberRiskEventDetailDTO dto = riskAdminService.getEventDetail(form.id(), form.createTime());
        if (dto == null) {
            return null;
        }
        MemberRiskEventDetailVO vo = new MemberRiskEventDetailVO();
        vo.setEvent(SolvelaBeanUtil.copy(dto.event(), MemberRiskEventVO.class));
        vo.setHits(SolvelaBeanUtil.copyList(dto.hits(), MemberRiskHitVO.class));
        return vo;
    }

    /** 详情的入参。两个字段缺一不可，所以不用 query string */
    public record MemberRiskEventDetailForm(
            @jakarta.validation.constraints.NotNull(message = "id 不能为空") Long id,
            @jakarta.validation.constraints.NotNull(message = "createTime 不能为空（分区裁剪要它）")
            LocalDateTime createTime) {
    }

    /**
     * 明文 → 摘要。
     *
     * <h3>🔴 必须先规范化再算摘要</h3>
     * {@code "138 0000 0000"} 与 {@code "13800000000"}、{@code "A@x.com"} 与 {@code "a@x.com"}
     * 算出来是不同的 hash。登录那一侧是规范化之后算的，这里不规范化就永远查不到 ——
     * 而页面只会显示「没有数据」，不会告诉你是因为多了个空格。
     *
     * <p>看着像手机号就按手机号规范化，否则按邮箱。两种都规范不出来时直接用原文的摘要 ——
     * 查不到总比悄悄用一个错的条件好。
     */
    private String hashIdentity(String identity) {
        if (SolvelaStringUtil.isBlank(identity)) {
            return null;
        }
        String normalized = MemberPhoneUtil.normalize(identity);
        if (normalized == null) {
            normalized = MemberEmailUtil.normalize(identity);
        }
        return piiHasher.hash(normalized == null ? identity.trim() : normalized);
    }
}
