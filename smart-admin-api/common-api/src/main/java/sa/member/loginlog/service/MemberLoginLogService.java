package sa.member.loginlog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.member.domain.form.MemberLoginLogQueryForm;
import sa.member.loginlog.dao.MemberLoginLogDao;
import sa.member.loginlog.domain.vo.MemberLoginLogVO;

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
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberLoginLogVO> list = memberLoginLogDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }


}
