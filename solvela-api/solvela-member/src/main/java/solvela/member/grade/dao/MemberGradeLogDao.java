package solvela.member.grade.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.member.MemberGradeLog;
import solvela.member.grade.domain.dto.MemberGradeLogDTO;
import solvela.member.grade.domain.query.MemberGradeLogQuery;

import java.util.List;

/**
 * 等级变更留痕 Dao。
 *
 * <p>⚠️ <b>只插不改</b>。留痕表被 UPDATE 过一次，它作为证据的价值就没了 ——
 * 所以这里没有也不该有任何 update 方法。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Mapper
public interface MemberGradeLogDao extends BaseMapper<MemberGradeLog> {

    /** 管理端分页 */
    List<MemberGradeLogDTO> queryPage(Page<?> page, @Param("queryForm") MemberGradeLogQuery queryForm);
}
