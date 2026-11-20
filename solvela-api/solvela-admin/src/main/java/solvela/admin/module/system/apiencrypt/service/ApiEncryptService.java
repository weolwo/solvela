package solvela.admin.module.system.apiencrypt.service;

/**
 * 接口加密、解密 Service
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/21 11:41:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

public interface ApiEncryptService {

    /**
     * 解密。
     *
     * <p>密文来自请求，是外部输入：解不开时抛 {@link solvela.exception.BusinessException}（用户级 PARAM_ERROR），
     * 既不打挂接口，也不把失败伪装成一个值。
     * 🔴 以前这里解不开就返回空串，登录时空串会被当成「用户输入的密码」继续往下比对，
     * 真正的原因（前后端密钥或算法对不上）只剩一行没有上下文的 ERROR 日志。
     */
    String decrypt(String data);

    /**
     * 加密。入参是服务端自己序列化的内容，加密失败只可能是系统故障，抛 {@link IllegalStateException}。
     *
     * <p>🔴 不能退回空串或明文：前者让前端拿到一个解不开的响应，后者正是加密注解要防的事。
     */
    String encrypt(String data);

}
