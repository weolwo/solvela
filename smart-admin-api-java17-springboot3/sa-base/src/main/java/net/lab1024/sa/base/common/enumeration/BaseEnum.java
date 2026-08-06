package net.lab1024.sa.base.common.enumeration;

import net.lab1024.sa.base.common.util.SmartCaseFormat;
import java.util.Objects;

/**
 * 枚举类接口
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2018-07-17 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public interface BaseEnum {

    /**
     * 获取枚举类的值
     *
     * @return
     */
    Object getValue();

    /**
     * 获取枚举类的说明
     *
     * @return String
     */
    String getDesc();

    /**
     * 比较参数是否与枚举类的value相同
     *
     * @param value
     * @return boolean
     */
    default boolean equalsValue(Object value) {
        return Objects.equals(getValue(), value);
    }

    /**
     * 比较枚举类是否相同
     *
     * @param baseEnum
     * @return boolean
     */
    default boolean equals(BaseEnum baseEnum) {
        return Objects.equals(getValue(), baseEnum.getValue()) && Objects.equals(getDesc(), baseEnum.getDesc());
    }

    /**
     * 返回枚举类的说明
     *
     * @param clazz 枚举类类对象
     * @return
     */

    /**
     * 返回枚举类的说明 (纯Java实现，移除Fastjson依赖)
     */
  public static String getInfo(Class<? extends BaseEnum> clazz) {
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
        String prefix = "  <br>  export const " + SmartCaseFormat.UPPER_CAMEL.to(SmartCaseFormat.UPPER_UNDERSCORE, clazz.getSimpleName()) + " = <br> ";
        return prefix + enumStr + " <br>";
    }
}
