package solvela.base.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PII 字段的读写拦截：<b>写库自动加密、读库自动解密</b>。
 *
 * <h3>为什么做成 TypeHandler，而不是在 Service 里手动调 encrypt/decrypt</h3>
 * 因为手动调<b>一定会被忘掉</b>。这张表的写入路径有四条（后台新增、后台编辑、Excel 新增导入、
 * 中奖时自动建履约单），将来商城下单还要再加一条；读的路径还有列表、详情、导出。
 * 只要有一条漏了，那一行就是明文进库 —— 而且<b>当场完全正常</b>，
 * 要等到有人翻库才发现「怎么这几行是明文」。
 *
 * <p>钉在 JDBC 边界上就没有「忘记」这个选项：不管上层是 MyBatis-Plus 的 {@code insert} /
 * {@code updateById}，还是 XML 里手写的 SQL，值都要经过这里。
 *
 * <h3>🔴 两处必须同时挂，少一处只生效一半</h3>
 * <ol>
 *   <li><b>实体字段</b>：{@code @TableField(typeHandler = PiiTypeHandler.class)}，
 *       并且实体上必须有 {@code @TableName(autoResultMap = true)} ——
 *       没有 {@code autoResultMap}，MyBatis-Plus <b>只在写的时候用 typeHandler，读的时候不用</b>，
 *       表现是「存进去是密文，查出来还是密文」，而且不报错。</li>
 *   <li><b>VO 的 resultMap</b>：{@code <result ... typeHandler="solvela.base.crypto.PiiTypeHandler"/>}。
 *       列表页走的是自己写的 resultMap，不吃实体上的注解。漏了就是页面上一片 {@code P1:...}。</li>
 * </ol>
 *
 * <h3>🔴 本类刻意<b>不是</b> Spring Bean，也刻意<b>不带</b> {@code @MappedTypes}</h3>
 * 两者任一都会引发同一场事故：{@code MybatisPlusAutoConfiguration} 会把容器里所有
 * {@code TypeHandler} 类型的 Bean 收集起来交给 {@code SqlSessionFactoryBean.setTypeHandlers}，
 * 而带 {@code @MappedTypes(String.class)} 注册进去的结果是 ——
 * <b>全库每一个 String 列都会被当成 PII 加解密</b>。
 * 那意味着菜单名、活动编码、物流单号统统变成 {@code P1:...} 进库。
 * <p>所以：本类由 MyBatis 按需 new，{@link PiiCipher} 通过 {@link PiiCipherHolder} 拿。
 *
 * @Date 2026-08-22
 */
public class PiiTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, PiiCipherHolder.get().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return PiiCipherHolder.get().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return PiiCipherHolder.get().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return PiiCipherHolder.get().decrypt(cs.getString(columnIndex));
    }
}
