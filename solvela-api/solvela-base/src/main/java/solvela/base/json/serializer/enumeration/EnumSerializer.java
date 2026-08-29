package solvela.base.json.serializer.enumeration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import solvela.base.constant.StringConst;
import solvela.enums.BaseEnum;
import solvela.base.util.SolvelaEnumUtil;
import solvela.base.util.SolvelaStringUtil;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * 枚举 序列化
 *
 * @author huke
 * @date 2024年6月29日
 */
public class EnumSerializer extends ValueSerializer<Object> {

    private Class<? extends BaseEnum> enumClazz;

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext serializers) {
        gen.writePOJO(value);
        String fieldName = gen.streamWriteContext().currentName() + "Desc";
        Object desc;
        // 多个枚举类 逗号分割
        if (value instanceof String && String.valueOf(value).contains(StringConst.SEPARATOR)) {
            desc = SolvelaStringUtil.splitConvertToIntList(String.valueOf(value), StringConst.SEPARATOR)
                    .stream().map(e -> SolvelaEnumUtil.getEnumDescByValue(e, enumClazz)).collect(Collectors.toList());

        } else if (value instanceof BaseEnum baseEnum) {
            // 字段类型已经是枚举本身（枚举化改造之后的形态）。
            // 这一支不能省：getEnumByValue 是拿 value 去比 getValue()，
            // 传进来的若是枚举实例，Objects.equals(Integer, 枚举) 恒为 false，
            // desc 会静默变成 null —— 页面上就是一列空白，接口照样 200。
            desc = baseEnum.getDesc();
        } else {
            BaseEnum anEnum = SolvelaEnumUtil.getEnumByValue(value, enumClazz);
            desc = null != anEnum ? anEnum.getDesc() : null;
        }
        gen.writePOJOProperty(fieldName, desc);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext prov, BeanProperty property)  {
        EnumSerialize annotation = property.getAnnotation(EnumSerialize.class);
        if (null == annotation) {
            return prov.findValueSerializer(property.getType());
        }
        enumClazz = annotation.value();
        return this;
    }
}