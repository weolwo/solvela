package solvela.member.verify.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.member.MemberVerify;
import solvela.member.verify.domain.query.MemberVerifyQuery;
import solvela.member.verify.domain.dto.MemberVerifyDTO;

import java.util.List;

/**
 * 会员实名信息（敏感，与主表分离） Dao
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */
@Mapper
public interface MemberVerifyDao extends BaseMapper<MemberVerify> {

    /**
     * 分页查询
     *
     * @param page      分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberVerifyDTO> queryPage(Page<?> page, @Param("queryForm") MemberVerifyQuery queryForm);

    /**
     * 单条详情（明文）。审核弹窗用 —— 让「下发完整证件号」这件事有唯一入口
     */
    MemberVerifyDTO getDetail(@Param("id") Long id);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberVerifyDTO> queryList(@Param("queryForm") MemberVerifyQuery queryForm);

}