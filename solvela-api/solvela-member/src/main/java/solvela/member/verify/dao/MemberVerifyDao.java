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

    /*
     * 🔴 审核闸门：带「仍是认证中」条件，返回影响行数，0 表示已被别人审核过或本就不是认证中。
     * 不要改回「读出来判一下再 updateById」—— 两个人同时点都会成功。迁移见 MemberVerifyStatusEnum#canTransitTo。
     */

    /** 审核通过：认证中 → 已认证，写数据库时钟的通过时间，清掉上次的驳回原因 */
    int markVerified(@Param("id") Long id);

    /** 审核驳回：认证中 → 认证失败 */
    int markRejected(@Param("id") Long id, @Param("failReason") String failReason);

}