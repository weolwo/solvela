package solvela.member.loginlog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.dao.SolvelaPageUtil;
import solvela.member.loginlog.domain.form.MemberLoginLogQueryForm;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.base.domain.ResponseDTO;
import solvela.member.loginlog.domain.vo.MemberLoginLogStatVO;
import solvela.member.loginlog.domain.vo.MemberLoginLogVO;

import java.util.List;

/**
 * 会员登录日志（append-only，按月分区） Service
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberLoginLogService {

    private final MemberLoginLogDao memberLoginLogDao;

    /**
     * 分页查询
     */
    public PageResult<MemberLoginLogVO> queryPage(MemberLoginLogQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberLoginLogVO> list = memberLoginLogDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }



    /**
     * 统计。<b>与列表共用同一套查询条件</b> —— 顶部筛选改了统计跟着变，
     * 两套条件的话运营会看到「统计说 100 次、列表只有 3 条」然后不知道信哪个。
     */
    public ResponseDTO<MemberLoginLogStatVO> queryStat(MemberLoginLogQueryForm queryForm) {
        MemberLoginLogStatVO stat = memberLoginLogDao.queryStat(queryForm);
        // 一条记录都没有时 SUM() 返回 null，直接下发会让前端把「0 次」渲染成空白
        return ResponseDTO.ok(stat == null ? emptyStat() : normalize(stat));
    }

    private MemberLoginLogStatVO emptyStat() {
        return normalize(new MemberLoginLogStatVO());
    }

    private MemberLoginLogStatVO normalize(MemberLoginLogStatVO stat) {
        stat.setTotalCount(nullToZero(stat.getTotalCount()));
        stat.setSuccessCount(nullToZero(stat.getSuccessCount()));
        stat.setFailCount(nullToZero(stat.getFailCount()));
        stat.setMemberCount(nullToZero(stat.getMemberCount()));
        stat.setSuccessMemberCount(nullToZero(stat.getSuccessMemberCount()));
        stat.setIpCount(nullToZero(stat.getIpCount()));
        return stat;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
