package solvela.member.verify.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.member.constant.MemberConst;
import solvela.member.verify.MemberVerifyMask;
import solvela.member.verify.dao.MemberVerifyDao;
import solvela.member.verify.domain.query.MemberVerifyQuery;
import solvela.member.verify.domain.dto.MemberVerifyDetailDTO;
import solvela.member.verify.domain.dto.MemberVerifyDTO;
import solvela.exception.BusinessException;

import java.util.List;

/**
 * 会员实名信息 Service
 *
 * <p><b>运营在这个页面只做一件事：审核</b>（通过 / 驳回）。姓名和身份证是用户提交的，
 * 后台改它们没有任何合法用途 —— 生成器留的那个「能改所有字段」的表单已经废弃。
 *
 * <p>三层防护各管一件事，缺一层都不行：
 * <ul>
 *   <li>{@code PiiTypeHandler} 加密 —— 防静态泄露（库被脱、备份被拷、DBA 直接 select）</li>
 *   <li>{@link MemberVerifyMask} 脱敏 —— 防有应用权限的人一屏看到几十个身份证</li>
 *   <li>列表 / 详情拆成两个接口 —— 防「本来只想看列表却顺手拿到了全量明文」</li>
 * </ul>
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberVerifyService {

    private final MemberVerifyDao memberVerifyDao;

    /**
     * 分页查询。<b>姓名与身份证在这里被脱敏</b>，明文不出这个方法。
     */
    public PageResult<MemberVerifyDTO> queryPage(MemberVerifyQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberVerifyDTO> list = memberVerifyDao.queryPage(page, queryForm);
        list.forEach(MemberVerifyService::mask);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 单条详情，<b>明文</b>。审核弹窗用 —— 核对证件真伪就是这个页面存在的理由，
     * 看不到完整号码没法干活。
     *
     * <p>它和列表分开，是为了让「下发完整证件号」成为一个可以单独授权、单独审计的动作。
     */
    public MemberVerifyDetailDTO detail(Long id) {
        MemberVerifyDTO row = memberVerifyDao.getDetail(id);
        if (row == null) {
            throw new BusinessException("实名记录不存在");
        }
        MemberVerifyDetailDTO vo = new MemberVerifyDetailDTO();
        vo.setId(row.getId());
        vo.setMemberId(row.getMemberId());
        vo.setMemberName(row.getMemberName());
        vo.setNickname(row.getNickname());
        vo.setRealName(row.getRealName());
        vo.setIdCard(row.getIdCard());
        vo.setVerifyStatus(row.getVerifyStatus());
        vo.setVerifyTime(row.getVerifyTime());
        vo.setFailReason(row.getFailReason());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /**
     * 审核通过。
     *
     * <p>只有「认证中」能被审核：已认证的再点一次会把 verify_time 刷成今天，
     * 那个时间是有业务含义的（合规审计要回答「什么时候通过的」）。
     * 这条规则压在 {@code markVerified} 的 WHERE 里，两个运营同时点，第二个影响 0 行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        if (id == null || memberVerifyDao.markVerified(id) == 0) {
            throw new BusinessException(NOT_AUDITABLE);
        }
    }

    /**
     * 审核驳回。<b>必须填原因</b> —— 用户在 C 端看到的就是这句话，
     * 不填的话他只知道失败了，不知道该改什么，只能反复重交。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String failReason) {
        if (StringUtils.isBlank(failReason)) {
            throw new BusinessException("请填写驳回原因，用户会看到这句话");
        }
        if (failReason.length() > MemberConst.MAX_FAIL_REASON_LENGTH) {
            throw new BusinessException("驳回原因最长 " + MemberConst.MAX_FAIL_REASON_LENGTH + " 字");
        }
        if (id == null || memberVerifyDao.markRejected(id, failReason.trim()) == 0) {
            throw new BusinessException(NOT_AUDITABLE);
        }
    }

    /** 闸门影响 0 行：记录不存在、本就不是认证中，或刚被别人审核过 —— 对运营是同一句话 */
    private static final String NOT_AUDITABLE = "只有「认证中」的记录可以审核，该记录可能已被他人处理，请刷新后重试";

    private static void mask(MemberVerifyDTO vo) {
        vo.setRealName(MemberVerifyMask.maskName(vo.getRealName()));
        vo.setIdCard(MemberVerifyMask.maskIdCard(vo.getIdCard()));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
