package solvela.app.config;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Controller;

/**
 * 组件扫描过滤器：<b>凡是共享模块（solvela.app 之外）下的 web controller，本服务一律不装配。</b>
 *
 * <p>本模块依赖 solvela-base 与 common-api 是为了复用领域层（service / manager / dao），
 * 它们自带的 controller 是管理端视角的接口，注册到 C 端端口上就是把后台摆到公网。
 *
 * <h3>🔴 为什么不用包名正则</h3>
 * 最初这里写的是 {@code @ComponentScan.Filter(REGEX, "sa\\..*\\.controller\\..*")} ——
 * 看着挺对，实际有洞：它要求类的全限定名里有 {@code .controller.} 这一段，而 solvela-base 里
 * <b>有两个 controller 压根不在 controller 包下</b>：
 * <pre>
 *   solvela.base.module.support.config.ConfigController        ← 漏
 *   solvela.base.module.support.table.TableColumnController    ← 漏
 * </pre>
 * 这不是假想 —— 实测过，那两条路由确实注册在了 1025 端口上（返回「未登录」
 * 而不是 404，说明 handler 存在，只是被拦截器挡了一下）。
 *
 * <p>⚠️ 上面两个类后来都随重构移交 solvela-admin 了，solvela-base 现在<b>一个 controller 都没有</b>。
 * 但<b>别因此把这里换回正则</b>：solvela-marketing / common-api 仍在共享包根下，
 * 而下一个把 controller 放在非常规包里的人，不会来看这段注释。
 * 「安全性取决于别人有没有把类放进约定的包」是个不该接受的前提：
 * 漏掉时没有任何报错，而代价是一个暴露在公网的后台接口。
 *
 * <p>改成按 {@link Controller} 注解判断之后，判据变成「它是不是一个 web 端点」本身，
 * 与包名、类名、目录都无关。{@code @RestController} 是 {@code @Controller} 的元注解，
 * 所以用 {@code hasMetaAnnotation} 一并覆盖。
 *
 * <h3>为什么还要限定包前缀</h3>
 * {@code @ComponentScan} 的 excludeFilters 对<b>所有</b> basePackages 生效 ——
 * 不判前缀的话，本模块自己 {@code solvela.app.**.controller} 下的接口会被一起排掉，
 * 症状是「所有接口 404，但应用启动完全正常」。
 *
 * @Date 2026-08-25
 */
public class SharedControllerExcludeFilter implements TypeFilter {

    /**
     * 全项目已并到 {@code solvela} 一个根包下（原先是 {@code sa} 与 app 两个根），
     * 「共享 controller」不再能靠根包区分，只能反过来：排 {@code solvela.**}，
     * 但放行本模块自己的 {@code solvela.app.**}，否则会退化成「所有接口 404」。
     */
    private static final String SHARED_PACKAGE_PREFIX = "solvela.";

    private static final String APP_PACKAGE_PREFIX = "solvela.app.";

    private static final String CONTROLLER_ANNOTATION = Controller.class.getName();

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
        String className = metadata.getClassName();
        if (!className.startsWith(SHARED_PACKAGE_PREFIX) || className.startsWith(APP_PACKAGE_PREFIX)) {
            return false;
        }
        // 直接标注 @Controller，或标注了以 @Controller 为元注解的注解（@RestController 就是）
        return metadata.hasAnnotation(CONTROLLER_ANNOTATION)
                || metadata.hasMetaAnnotation(CONTROLLER_ANNOTATION);
    }
}
