package sa.base.common.swagger;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import sa.base.common.enumeration.BaseEnum;
import sa.base.common.validator.enumeration.CheckEnum;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;

/**
 *
 * 自定义枚举类文档
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/12/25 23:28:51
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
/**
 * ⚠️ {@code @ConditionalOnClass} 不能删：prod 包里 springdoc / swagger 整套已被排除，
 * 本类 implements 的 {@link PropertyCustomizer} 届时不存在。Spring 的组件扫描用 ASM 读元数据，
 * 条件不满足就不会去加载这个类，从而避免 NoClassDefFoundError。
 * 同包的 SmartOperationCustomizer 不需要，它由 SwaggerConfig 手工 new，
 * 而 SwaggerConfig 本身有 @Conditional(SystemEnvironmentConfig.class) 只在 dev/test 生效。
 */
@ConditionalOnClass(PropertyCustomizer.class)
@Component
public class SchemaEnumPropertyCustomizer implements PropertyCustomizer {

    @Override
    public Schema customize(Schema schema, AnnotatedType type) {
        if (type.getCtxAnnotations() == null) {
            return schema;
        }

        StringBuilder description = new StringBuilder();
        for (Annotation ctxAnnotation : type.getCtxAnnotations()) {
            if (ctxAnnotation.annotationType().equals(CheckEnum.class) && ((CheckEnum) ctxAnnotation).required()) {
                description.append("<font style=\"color: red;\">【必填】</font>");
            }
        }

        for (Annotation ctxAnnotation : type.getCtxAnnotations()) {
            if (ctxAnnotation.annotationType().equals(SchemaEnum.class)) {
                description.append(((SchemaEnum) ctxAnnotation).desc());
                Class<? extends BaseEnum> clazz = ((SchemaEnum) ctxAnnotation).value();
                description.append(BaseEnum.getInfo(clazz));
            }
        }

        if (description.length() > 0) {
            schema.setDescription(description.toString());
        }
        return schema;
    }

}
