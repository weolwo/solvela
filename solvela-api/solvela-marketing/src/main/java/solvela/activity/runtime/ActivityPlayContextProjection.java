package solvela.activity.runtime;

import org.springframework.stereotype.Component;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptContextProjection;

/**
 * 让脚本函数把 {@link ActivityPlayContext} 直接声明为首参。
 *
 * <p>注册这一个 bean 之后，玩法编排场景里的函数不必再声明 {@code EngineContext}、
 * 也不必知道内部通道用了哪些字符串键 —— 签名就是它需要什么。
 */
@Component
public class ActivityPlayContextProjection implements ScriptContextProjection<ActivityPlayContext> {

    @Override
    public Class<ActivityPlayContext> contextType() {
        return ActivityPlayContext.class;
    }

    @Override
    public ActivityPlayContext project(EngineContext context, String functionName) {
        return ActivityPlayContext.of(context, functionName);
    }
}
