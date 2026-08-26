package solvela.member.verify.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.PageResult;
import solvela.base.domain.RequestUser;
import solvela.base.domain.ResponseDTO;
import solvela.base.dao.SolvelaPageUtil;
import solvela.member.constant.MemberConst;
import solvela.member.verify.MemberVerifyMask;
import solvela.member.verify.dao.MemberVerifyDao;
import solvela.member.MemberVerify;
import solvela.member.verify.domain.form.MemberVerifyQueryForm;
import solvela.member.verify.domain.vo.MemberVerifyDetailVO;
import solvela.member.verify.domain.vo.MemberVerifyVO;
import solvela.member.verify.manager.MemberVerifyManager;

import java.time.LocalDateTime;
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
    private final MemberVerifyManager memberVerifyManager;

    /**
     * 分页查询。<b>姓名与身份证在这里被脱敏</b>，明文不出这个方法。
     */
    public PageResult<MemberVerifyVO> queryPage(MemberVerifyQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberVerifyVO> list = memberVerifyDao.queryPage(page, queryForm);
        list.forEach(MemberVerifyService::mask);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 单条详情，<b>明文</b>。审核弹窗用 —— 核对证件真伪就是这个页面存在的理由，
     * 看不到完整号码没法干活。
     *
     * <p>它和列表分开，是为了让「下发完整证件号」成为一个可以单独授权、单独审计的动作。
     */
    public ResponseDTO<MemberVerifyDetailVO> detail(Long id) {
        MemberVerifyVO row = memberVerifyDao.getDetail(id);
        if (row == null) {
            return ResponseDTO.userErrorParam("实名记录不存在");
        }
        MemberVerifyDetailVO vo = new MemberVerifyDetailVO();
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
        return ResponseDTO.ok(vo);
    }

    /**
     * 审核通过。
     *
     * <p>只有「认证中」能被审核：已认证的再点一次会把 verify_time 刷成今天，
     * 那个时间是有业务含义的（合规审计要回答「什么时候通过的」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> approve(Long id, RequestUser user) {
        MemberVerify verify = requireAuditable(id);
        if (verify == null) {
            return ResponseDTO.userErrorParam("只有「认证中」的记录可以审核");
        }
        MemberVerify update = new MemberVerify();
        update.setId(id);
        update.setVerifyStatus(MemberConst.VERIFY_STATUS_VERIFIED);
        update.setVerifyTime(LocalDateTime.now());
        // 通过时清掉上一次的驳回原因，否则界面上会出现「已认证」旁边挂着一条失败理由
        update.setFailReason(null);
        memberVerifyDao.updateById(update);
        return ResponseDTO.ok();
    }

    /**
     * 审核驳回。<b>必须填原因</b> —— 用户在 C 端看到的就是这句话，
     * 不填的话他只知道失败了，不知道该改什么，只能反复重交。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> reject(Long id, String failReason, RequestUser user) {
        if (StringUtils.isBlank(failReason)) {
            return ResponseDTO.userErrorParam("请填写驳回原因，用户会看到这句话");
        }
        if (failReason.length() > MemberConst.MAX_FAIL_REASON_LENGTH) {
            return ResponseDTO.userErrorParam("驳回原因最长 " + MemberConst.MAX_FAIL_REASON_LENGTH + " 字");
        }
        MemberVerify verify = requireAuditable(id);
        if (verify == null) {
            return ResponseDTO.userErrorParam("只有「认证中」的记录可以审核");
        }
        MemberVerify update = new MemberVerify();
        update.setId(id);
        update.setVerifyStatus(MemberConst.VERIFY_STATUS_FAILED);
        update.setFailReason(failReason.trim());
        // 驳回不写 verify_time：那一列的语义是「认证通过时间」，驳回没有通过
        memberVerifyDao.updateById(update);
        return ResponseDTO.ok();
    }

    /** 取一条可审核的记录；不存在或状态不对返回 null */
    private MemberVerify requireAuditable(Long id) {
        MemberVerify verify = id == null ? null : memberVerifyManager.getById(id);
        if (verify == null) {
            return null;
        }
        return MemberConst.VERIFY_STATUS_PENDING == nullToZero(verify.getVerifyStatus()) ? verify : null;
    }

    private static void mask(MemberVerifyVO vo) {
        vo.setRealName(MemberVerifyMask.maskName(vo.getRealName()));
        vo.setIdCard(MemberVerifyMask.maskIdCard(vo.getIdCard()));
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
