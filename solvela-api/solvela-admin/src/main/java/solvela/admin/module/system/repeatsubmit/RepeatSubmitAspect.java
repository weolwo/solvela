package solvela.admin.module.system.repeatsubmit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import solvela.base.exception.BusinessException;
import solvela.admin.module.system.repeatsubmit.annotation.RepeatSubmit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@AllArgsConstructor
public class RepeatSubmitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    private final RepeatSubmitKeyGenerator generator;

    @Before("@annotation(repeatSubmit)")
    public void doBefore(JoinPoint point, RepeatSubmit repeatSubmit) throws Throwable {
        // 如果注解不为0 则使用注解数值
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String key = generator.createKey(point, request);
        Boolean isSuccess = redisTemplate.opsForValue().setIfAbsent(key, "1", repeatSubmit.interval(), repeatSubmit.timeUnit());
        if (!Boolean.TRUE.equals(isSuccess)) {
            throw new BusinessException(repeatSubmit.message());
        }
    }
}
