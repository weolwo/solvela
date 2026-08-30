package solvela.base.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
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
     * 让 MyBatis 用<b>枚举的 value</b> 而不是<b>枚举名</b>来读写数据库列。
     *
     * <h3>这一条是承重墙，删掉会静默炸</h3>
     * {@code BaseEnum} 继承了 {@code IEnum}，但<b>光有 IEnum 是不够的</b> ——
     * MyBatis 默认的 {@code EnumTypeHandler} 按 {@link Enum#name()} 映射，
     * 于是从库里读出 int 值 10 时它会去找一个名叫 "10" 的常量，抛
     * {@code IllegalArgumentException: No enum constant XxxEnum.10}。
     *
     * <p>把默认处理器换成 MP 的 {@link MybatisEnumTypeHandler}，它才认 {@code IEnum.getValue()}。
     *
     * <p>三条读写路径都依赖它：BaseMapper 的增删改查、手写 XML 的 {@code resultType}
     * 自动映射、以及把枚举当查询参数（{@code #{query.type}}）。
     * 其中 <b>resultType 那条最隐蔽</b>：它不看实体上的任何注解，只按属性类型找 TypeHandler。
     *
     * <p>放在这里而不是四份 yaml 的 {@code mybatis-plus.configuration.default-enum-type-handler}：
     * 这是全局语义，不该有「某个环境配漏了」的可能。
     *
     * <p>回归用例见 {@code DataTracerEnumMappingTest}。
     */
    @Bean
    public ConfigurationCustomizer enumTypeHandlerCustomizer() {
        return configuration -> configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
    }

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
