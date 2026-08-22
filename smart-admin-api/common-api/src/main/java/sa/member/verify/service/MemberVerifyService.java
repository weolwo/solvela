package sa.member.verify.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.member.verify.dao.MemberVerifyDao;
import sa.member.verify.domain.form.MemberVerifyQueryForm;
import sa.member.verify.domain.vo.MemberVerifyVO;

import java.util.List;

/**
 * 会员实名信息（敏感，与主表分离） Service
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
     * 分页查询
     */
    public PageResult<MemberVerifyVO> queryPage(MemberVerifyQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberVerifyVO> list = memberVerifyDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }


}
