package solvela.web.swagger;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import solvela.enums.BaseEnum;
import solvela.base.util.SolvelaCaseFormat;
import solvela.base.validation.enumeration.CheckEnum;
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
 * 同包的 SolvelaOperationCustomizer 不需要，它由 SwaggerConfig 手工 new，
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
                description.append(enumDoc(clazz));
            }
        }

        if (description.length() > 0) {
            schema.setDescription(description.toString());
        }
        return schema;
    }

    /**
     * 把枚举渲染成前端可直接粘贴的 TS 常量，拼进 swagger 的字段描述里。
     *
     * <p>原先挂在 {@code BaseEnum} 接口上，但那让一个<b>领域词汇的接口</b>依赖了
     * 大小写转换工具，进而挡住了 BaseEnum 下沉到 solvela-model。
     * 它本来就只有这一个调用方，而且干的是「生成接口文档」的活 —— 属于 swagger 定制器。
     */
    private static String enumDoc(Class<? extends BaseEnum> clazz) {
        BaseEnum[] enums = clazz.getEnumConstants();
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        for (int i = 0; i < enums.length; i++) {
            BaseEnum e = enums[i];
            // 拼接外层的 Key (比如: NORMAL: { )
            sb.append("\t").append(e.toString()).append(": {");

            // 拼接 value (如果是字符串，包上单引号)
            sb.append("value: ");
            if (e.getValue() instanceof String) {
                sb.append("'").append(e.getValue()).append("'");
            } else {
                sb.append(e.getValue());
            }
            sb.append(", ");

            // 拼接 desc (描述通常都是字符串，包上单引号)
            sb.append("desc: ");
            if (e.getDesc() instanceof String) {
                sb.append("'").append(e.getDesc()).append("'");
            } else {
                sb.append(e.getDesc());
            }

            sb.append("}");
            // 如果不是最后一个，加个逗号
            if (i < enums.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");

        // 还原他原本的 HTML 替换逻辑
        String enumStr = sb.toString();
        enumStr = enumStr.replace("\t", "&nbsp;&nbsp;");
        enumStr = enumStr.replace("\n", "<br>");

        // 拼接 export const 开头
        String prefix = "  <br>  export const " + SolvelaCaseFormat.UPPER_CAMEL.to(SolvelaCaseFormat.UPPER_UNDERSCORE, clazz.getSimpleName()) + " = <br> ";
        return prefix + enumStr + " <br>";
    }
}
