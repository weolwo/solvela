package sa.base.common.crypto;

import org.springframework.stereotype.Component;

/**
 * 把 {@link PiiCipher} 从 Spring 容器里递给 {@link PiiTypeHandler}。
 *
 * <p><b>为什么需要这么一个中间物</b>：TypeHandler 是 MyBatis 用<b>反射调无参构造</b> new 出来的
 * （{@code @TableField(typeHandler = ...)} 这条路），拿不到容器里的 Bean；
 * 而 PiiTypeHandler 又<b>不能</b>自己变成 Spring Bean（理由见那个类的注释：
 * 会被 MyBatis-Plus 注册成全局 String 处理器，把整库的字符串列都加密掉）。
 * 于是留一个只做一件事的 Bean：启动时把 cipher 挂到静态字段上。
 *
 * @Date 2026-08-22
 */
@Component
public class PiiCipherHolder {

    private static volatile PiiCipher cipher;

    public PiiCipherHolder(PiiCipher piiCipher) {
        cipher = piiCipher;
    }

    /**
     * 🔴 取不到时<b>直接抛</b>，绝不降级成「原样读写」。
     * 降级的后果是个人信息明文进库，而且一切看起来都正常 —— 正是这套机制要防的那件事。
     */
    public static PiiCipher get() {
        PiiCipher c = cipher;
        if (c == null) {
            throw new IllegalStateException(
                    "PiiCipher 尚未初始化：PiiTypeHandler 在 Spring 容器就绪之前被使用了。"
                  + "这里不会降级成明文读写 —— 那会让个人信息明文进库且无人察觉。");
        }
        return c;
    }
}
