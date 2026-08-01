package net.lab1024.sa.base.common.json.serializer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import net.lab1024.sa.base.module.support.datamasking.DataMasking;
import net.lab1024.sa.base.module.support.datamasking.DataMaskingTypeEnum;
import net.lab1024.sa.base.module.support.datamasking.DataMaskingUtil;

import java.io.IOException;


//<editor-fold desc="工作原理">
     /*
     1. 核心操作链条（从 Controller 到浏览器）
    当你请求一个接口并返回数据时，内部经历了一场“接力赛”：

    Controller 返回对象：
    你的代码执行完毕，return UserDTO。此时，对象里的手机号还是原封不动的 13812345678。

    RequestResponseBodyMethodProcessor 介入：
    Spring MVC 发现你的方法上有 @ResponseBody（或者类上有 @RestController），于是派出一个叫“返回值处理器”的干将。

    寻找消息转换器（MessageConverter）：
    处理器看了一眼 HTTP Header（Accept: application/json），心领神会，去叫 MappingJackson2HttpMessageConverter 来干活。

    Jackson 启动序列化：
    Converter 内部持有一个 ObjectMapper。它开始逐个扫描你的 DTO 字段。

    上下文探测（关键一步）：
    当 Jackson 扫描到贴了 @JsonSerialize(using = DataMaskingSerializer.class) 的字段时，它不会直接调用 serialize 方法，而是先调用你的 createContextual。

    为什么？ 因为 Jackson 是单例的。为了知道这个字段到底该用“手机号”脱敏还是“身份证”脱敏，它必须通过 BeanProperty 拿到注解信息，然后现开一个带参数的新实例（也就是你代码里 new DataMaskingSerializer(type) 的地方）。

    执行脱敏序列化：
    拿到新实例后，Jackson 调用 serialize 方法。你的代码把 13812345678 传给 DataMaskingUtil，吐出 138****5678，并写进 HTTP 响应流（Response Body）。

    浏览器接收：
    前端收到的 JSON 已经是“打码”后的了。

            2. 谁在管理这个类的生命周期？
    Jackson 的 SerializerProvider： 它是总管家。它负责缓存这些序列化器，避免每次请求都重新解析注解（性能优化点就在这）。

    上下文感知： 你的类实现了 ContextualSerializer，这让它具备了“读注解”的能力。如果没有这个接口，Jackson 只能傻傻地调用默认构造函数，那就没法根据不同的脱敏类型（手机、姓名）做区分了。

            3. 这个链条牛逼在哪？
    非侵入性： 你的业务代码（Service 层）完全不需要知道“脱敏”这件事。你查出来的 DTO 是干净的、原始的。

    线程安全： 每一个需要脱敏的字段，在 Jackson 内部都会被关联到一个特定的序列化器实例。因为你在 createContextual 里返回的是 new 出来的实例，所以不存在并发竞争成员变量的问题。

    性能极致： Jackson 做了大量的缓存工作。只有第一次访问某个 DTO 时会去解析注解，之后几乎是零开销运行
*/
//</editor-fold>

public class DataMaskingSerializer extends ValueSerializer<Object> {

    private final DataMaskingTypeEnum type;

    // 默认构造函数（Jackson 必须）
    public DataMaskingSerializer() {
        this.type = DataMaskingTypeEnum.COMMON;
    }

    public DataMaskingSerializer(DataMaskingTypeEnum dataMaskingSerializer) {
        this.type = dataMaskingSerializer;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // 直接调用脱敏逻辑，不涉及任何反射 set 操作
        gen.writeString(DataMaskingUtil.apply(value, type));
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext prov, BeanProperty property)  {
        if (property != null) {
            DataMasking annotation = property.getAnnotation(DataMasking.class);
            if (annotation != null) {
                // 重点：每次返回一个新实例，确保线程安全，且不同字段互不干扰
                return new DataMaskingSerializer(annotation.value());
            }
        }
        return prov.findValueSerializer(property.getType());
    }
}
//</editor-fold>
//endregion


