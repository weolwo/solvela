package solvela.member.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.Member;
import solvela.member.domain.form.MemberQueryForm;
import solvela.member.domain.vo.MemberVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 会员主表（{@code t_member}）—— <b>只读查询</b>。
 *
 * <p>本 Dao 刻意<b>不</b>继承 {@code BaseMapper<Member>}，也刻意不建 Member 实体：
 * 会员域的注册/资料维护尚未开工（见交接文档 §13.7），现在把一个 20 多个字段的实体
 * 先摆在这里，等真开工时必然要按那时的需求重写一遍 —— 中间这段时间它只会给人
 * 「会员模块已经有了」的错觉。这里只提供关联键换成 {@code member_id} 之后
 * <b>十张业务表实际需要的两件事</b>：
 * <ol>
 *   <li>会员号 → 账号：单据类要落<b>展示快照</b>（{@code member_name}）；</li>
 *   <li>账号 → 会员号：后台按账号找人、Excel 导入填的是账号。</li>
 * </ol>
 *
 * <p>🔴 <b>查询一律走 member_id / member_name 的索引</b>，不要在这里加按昵称模糊搜之类的方法：
 * {@code nickname} 上没有索引，而且它<b>不是</b>唯一的（DDL 注释原文「任何地方都不许拿它做关联键」）。
 *
 * @Date 2026-08-22
 */
@Mapper
public interface MemberDao extends BaseMapper<Member> {

    /**
     * 分页查询
     *
     * @param page 分页参数
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberVO> queryPage(Page<?> page, @Param("queryForm") MemberQueryForm queryForm);

    /**
     * 列表查询 (无分页)
     *
     * @param queryForm 查询表单
     * @return 列表数据
     */
    List<MemberVO> queryList(@Param("queryForm") MemberQueryForm queryForm);

    /**
     * 按会员号取账号（主键点查）。查不到返回 null。
     */
    @Select("SELECT member_name FROM t_member WHERE member_id = #{memberId}")
    String selectMemberNameById(@Param("memberId") Long memberId);

    /**
     * 按账号取会员号（走 uk_mbr_name）。查不到返回 null。
     */
    @Select("SELECT member_id FROM t_member WHERE member_name = #{memberName}")
    Long selectMemberIdByName(@Param("memberName") String memberName);

    /**
     * 批量：账号 → 会员号。给 Excel 导入这种「一次几百行」的场景用 ——
     * 逐行点查会把一次导入变成几百次往返。
     *
     * <p>返回 {@code memberName -> {memberName, memberId}}，由调用方拍平。
     * MyBatis 的 {@code @MapKey} 只能给出这种两层结构，为它单建一个 record
     * 反而多一个只在这里用一次的类型。
     *
     * <p>🔴 <b>两个列必须显式起驼峰别名</b>，不能直接 {@code SELECT member_name, member_id}：
     * 结果类型是 {@code Map} 时，键来自列标签，而 MyBatis-Plus 的 {@code MybatisMapWrapper}
     * 会按 {@code mapUnderscoreToCamelCase}（MP 默认<b>开启</b>）把下划线列名转成驼峰。
     * 于是「键到底叫 member_id 还是 memberId」取决于配置 —— 猜错的表现是
     * {@code get()} 返回 null 后 NPE，而且只在真有数据时才炸。起了别名两种配置下都一样。
     */
    @MapKey("memberName")
    @Select("""
            <script>
            SELECT member_name AS memberName, member_id AS memberId FROM t_member
             WHERE member_name IN
            <foreach collection='memberNames' item='n' open='(' separator=',' close=')'>#{n}</foreach>
            </script>
            """)
    Map<String, Map<String, Object>> selectIdMapByNames(@Param("memberNames") Collection<String> memberNames);

}
