package solarx.app.config;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Controller;

/**
 * 组件扫描过滤器：<b>凡是 {@code sa.**} 下的 web controller，本服务一律不装配。</b>
 *
 * <p>本模块依赖 sa-base 与 common-api 是为了复用领域层（service / manager / dao），
 * 它们自带的 controller 是管理端视角的接口，注册到 C 端端口上就是把后台摆到公网。
 *
 * <h3>🔴 为什么不用包名正则</h3>
 * 最初这里写的是 {@code @ComponentScan.Filter(REGEX, "sa\\..*\\.controller\\..*")} ——
 * 看着挺对，实际有洞：它要求类的全限定名里有 {@code .controller.} 这一段，而 sa-base 里
 * <b>有两个 controller 压根不在 controller 包下</b>：
 * <pre>
 *   sa.base.module.support.config.ConfigController        ← 漏
 *   sa.base.module.support.table.TableColumnController    ← 漏
 * </pre>
 * 这不是假想 —— 实测过，那两条路由确实注册在了 1025 端口上（返回「未登录」
 * 而不是 404，说明 handler 存在，只是被拦截器挡了一下）。
 *
 * <p>⚠️ 上面两个类后来都随重构移交 sa-admin 了，sa-base 现在<b>一个 controller 都没有</b>。
 * 但<b>别因此把这里换回正则</b>：sa-marketing / common-api 仍在 {@code sa.} 下，
 * 而下一个把 controller 放在非常规包里的人，不会来看这段注释。
 * 「安全性取决于别人有没有把类放进约定的包」是个不该接受的前提：
 * 漏掉时没有任何报错，而代价是一个暴露在公网的后台接口。
 *
 * <p>改成按 {@link Controller} 注解判断之后，判据变成「它是不是一个 web 端点」本身，
 * 与包名、类名、目录都无关。{@code @RestController} 是 {@code @Controller} 的元注解，
 * 所以用 {@code hasMetaAnnotation} 一并覆盖。
 *
 * <h3>为什么还要限定 sa. 前缀</h3>
 * {@code @ComponentScan} 的 excludeFilters 对<b>所有</b> basePackages 生效 ——
 * 不判前缀的话，本模块自己 {@code solarx.app.**.controller} 下的接口会被一起排掉，
 * 症状是「所有接口 404，但应用启动完全正常」。
 *
 * @Date 2026-08-25
 */
public class SaControllerExcludeFilter implements TypeFilter {

    private static final String SA_PACKAGE_PREFIX = "sa.";

    private static final String CONTROLLER_ANNOTATION = Controller.class.getName();

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
        if (!metadata.getClassName().startsWith(SA_PACKAGE_PREFIX)) {
            return false;
        }
        // 直接标注 @Controller，或标注了以 @Controller 为元注解的注解（@RestController 就是）
        return metadata.hasAnnotation(CONTROLLER_ANNOTATION)
                || metadata.hasMetaAnnotation(CONTROLLER_ANNOTATION);
    }
}
