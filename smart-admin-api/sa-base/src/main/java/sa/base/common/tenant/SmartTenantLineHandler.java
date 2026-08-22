package sa.base.common.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import sa.base.common.constant.TenantConst;

/**
 * 多租户 SQL 改写规则。
 *
 * <p>配合 {@code TenantLineInnerInterceptor}，让 MyBatis-Plus 自动给
 * {@link TenantConst#TENANT_TABLES} 里那些表的 SQL 追加 {@code tenant_id = ?}。
 *
 * <p><b>为什么要拦截器，而不是各处手写 where 条件</b>：
 * 全项目 61 个自定义 Mapper XML + 大量 LambdaQueryWrapper，
 * 靠人逐条补租户条件必然漏，而漏掉的表现是<b>跨租户数据泄露</b> ——
 * 查出来的数据看起来一切正常，只是混进了别人的。这类问题没有任何报错能提示你。
 *
 * @Date 2026-08-22
 */
public class SmartTenantLineHandler implements TenantLineHandler {

    @Override
    public Expression getTenantId() {
        return new StringValue(TenantContext.get());
    }

    @Override
    public String getTenantIdColumn() {
        return TenantConst.TENANT_COLUMN;
    }

    /**
     * 返回 true = 这张表<b>不</b>做租户过滤。
     *
     * <p>白名单语义：只有明确列入 {@link TenantConst#TENANT_TABLES} 的才参与。
     * 理由见那个常量的注释 —— 两种漏判的代价不对称，漏掉白名单只是「暂时没隔离」，
     * 而黑名单漏掉会给没有该列的表塞进 {@code tenant_id}，直接 SQL 报错。
     */
    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return true;
        }
        // 别名/反引号/库名前缀都可能出现，统一剥一层再判
        String t = tableName.replace("`", "").trim();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return !TenantConst.TENANT_TABLES.contains(t.toLowerCase());
    }

    /**
     * INSERT 时，如果调用方<b>已经显式写了 tenant_id</b>，就别再自动追加一遍
     * （否则会出现两个同名列，MySQL 直接报 Column 'tenant_id' specified twice）。
     *
     * <p>本项目里有十几处 {@code setTenantId(TenantConst.DEFAULT_TENANT_ID)} 属于这种情况，
     * 它们不需要为了上拦截器而全部删掉 —— 默认实现已经处理了这个场景，
     * 这里显式重写只是把这件事写明白，免得后人以为要去清理那些赋值。
     */
    @Override
    public boolean ignoreInsert(java.util.List<net.sf.jsqlparser.schema.Column> columns, String tenantIdColumn) {
        return columns.stream()
                .map(net.sf.jsqlparser.schema.Column::getColumnName)
                .anyMatch(c -> c.replace("`", "").equalsIgnoreCase(tenantIdColumn));
    }
}
