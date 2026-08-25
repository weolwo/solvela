package solvela.admin.module.system.repeatsubmit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import solvela.base.common.util.JsonUtils;
import solvela.base.common.util.SolvelaRequestUtil;
import solvela.base.common.util.SolvelaStringUtil;
import solvela.base.constant.RedisKeyConst;
import org.aspectj.lang.JoinPoint;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

public class DefaultMd5KeyGenerator implements RepeatSubmitKeyGenerator {
    @Override
    public String createKey(JoinPoint point, HttpServletRequest request) {

        String url = request.getRequestURL().toString();
        Long userId = SolvelaRequestUtil.getRequestUserId();
        String reqParams = argsArrayToString(point.getArgs());
        String md5 = md5Hex(SolvelaStringUtil.join(":", userId, url, reqParams));
        // 唯一标识
        return String.join(":", RedisKeyConst.REPEAT_SUBMIT, md5);
    }


    /**
     * MD5 十六进制小写串。
     *
     * 这里只是把「同一个用户 + 同一个 URL + 同一份参数」压成一个定长 redis key，
     * 不做任何安全用途 —— MD5 早已不能用于口令或签名，别照抄到那种地方去。
     */
    private static String md5Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 必须实现的算法，走不到这里
            throw new IllegalStateException(e);
        }
    }

    /**
     * 参数拼装
     */
    private String argsArrayToString(Object[] paramsArray) {
        StringJoiner params = new StringJoiner(" ");
        if (paramsArray == null || paramsArray.length == 0) {
            return params.toString();
        }
        for (Object o : paramsArray) {
            if (o != null && !isFilterObject(o)) {
                params.add(JsonUtils.toJson(o));
            }
        }
        return params.toString();
    }

    /**
     * 判断是否需要过滤的对象。
     *
     * @param o 对象信息。
     * @return 如果是需要过滤的对象，则返回true；否则返回false。
     */
    @SuppressWarnings("rawtypes")
    public boolean isFilterObject(final Object o) {
        Class<?> clazz = o.getClass();
        if (clazz.isArray()) {
            return clazz.getComponentType().isAssignableFrom(MultipartFile.class);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Collection collection = (Collection) o;
            for (Object value : collection) {
                return value instanceof MultipartFile;
            }
        } else if (Map.class.isAssignableFrom(clazz)) {
            Map map = (Map) o;
            for (Object value : map.values()) {
                return value instanceof MultipartFile;
            }
        }
        return o instanceof MultipartFile || o instanceof HttpServletRequest || o instanceof HttpServletResponse;
    }
}
