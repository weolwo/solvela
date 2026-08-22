package sa.base.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import sa.base.common.tenant.SmartTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;

/**
 * mp 插件
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-09-02 20:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@EnableTransactionManagement
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * <p>🔴 <b>顺序不能调换：租户必须在分页之前。</b>
     * 分页拦截器会把原 SQL 包成 {@code SELECT COUNT(*) FROM (原SQL)} 并改写 limit；
     * 如果它先跑，租户拦截器拿到的就是被包装过的 SQL，改写位置会错 ——
     * 表现是<b>分页总数按全租户算、当前页数据却按单租户查</b>，
     * 页码点到后面就是空白页，而且不报任何错。
     * MyBatis-Plus 官方文档对这个顺序有明确要求。
     */
    @Bean
    public MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new SmartTenantLineHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    /**
     * 自定义 SQL 注入器
     */
    @Bean
    public DefaultSqlInjector customSqlInjector() {
        return new DefaultSqlInjector() {
            @Override
            public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
                // 1. 先拿到原版自带的所有基础方法 (insert, selectById 等)
                List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);

                // 第一个参数："insertBatch" 就是你最终在 Mapper 里要用的名字。
                // 第二个参数：过滤规则。t -> !t.isLogicDelete() 意思是忽略逻辑删除字段。你也可以直接传 t -> true（全字段插入）。
                methodList.add(new InsertBatchSomeColumn("insertBatch", t -> true));
                return methodList;
            }
        };
    }
}
