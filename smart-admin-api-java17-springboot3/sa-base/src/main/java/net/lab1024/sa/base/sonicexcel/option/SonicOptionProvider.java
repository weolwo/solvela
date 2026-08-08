package net.lab1024.sa.base.sonicexcel.option;

import net.lab1024.sa.base.sonicexcel.converter.SonicContext;

import java.util.List;

/**
 * 导入模板里某一列的可选值来源。
 *
 * <p>和 {@link net.lab1024.sa.base.sonicexcel.converter.SonicConverter} 同样的约定：
 * 单例、无可变状态，但<b>允许注入 Spring 单例依赖</b>（字典服务之类）。
 * 需要参数时从 {@link SonicContext#element()} 上读自己的配置注解。
 *
 * @Date 2026-08-08
 */
public interface SonicOptionProvider {

    List<String> options(SonicContext ctx);

    /**
     * 不提供选项，注解的默认值。
     */
    final class None implements SonicOptionProvider {
        @Override
        public List<String> options(SonicContext ctx) {
            return List.of();
        }
    }
}
