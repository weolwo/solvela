package sa.base.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.injector.methods.InsertBatchSomeColumn;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
     * <p>曾经这里还挂着 {@code TenantLineInnerInterceptor}（v3.71.0 启用）。
     * v3.73.0 起<b>整个租户维度被删除</b>：全库 27 张表的 {@code tenant_id} 实测
     * 一行非默认值都没有，系统从来就是单租户跑的，那一列与那套拦截器只是空转。
     * 将来真要做多租户，重新加回来是一次索引重建，比长期维护一个永远为真的过滤条件划算。
     */
    @Bean
    public MybatisPlusInterceptor paginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
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
