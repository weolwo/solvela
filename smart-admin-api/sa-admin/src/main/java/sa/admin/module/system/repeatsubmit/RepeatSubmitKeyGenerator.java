package sa.admin.module.system.repeatsubmit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;

/**
 * 定义一个 放重复提交 key 的接口
 */
public interface RepeatSubmitKeyGenerator {

    String createKey(JoinPoint point, HttpServletRequest request);
}
