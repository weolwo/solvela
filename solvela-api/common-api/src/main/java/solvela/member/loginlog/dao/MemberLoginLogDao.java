package solvela.member.loginlog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.member.loginlog.domain.form.MemberLoginLogQueryForm;
import solvela.member.MemberLoginLog;
import solvela.member.loginlog.domain.vo.MemberLoginLogStatVO;
import solvela.member.loginlog.domain.vo.MemberLoginLogVO;

import java.util.List;

/**
 * 会员登录日志（append-only，按月分区） Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 20:58:39
 * @Copyright weolwo
 */
@Mapper
public interface MemberLoginLogDao extends BaseMapper<MemberLoginLog> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberLoginLogVO> queryPage(Page<?> page, @Param("queryForm") MemberLoginLogQueryForm queryForm);

    /**
     * 统计：一趟 SQL 出全部指标，条件与列表复用同一段 query_conditions
     */
    MemberLoginLogStatVO queryStat(@Param("queryForm") MemberLoginLogQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberLoginLogVO> queryList(@Param("queryForm") MemberLoginLogQueryForm queryForm);

}