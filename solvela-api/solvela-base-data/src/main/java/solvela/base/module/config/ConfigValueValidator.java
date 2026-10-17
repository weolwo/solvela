package solvela.base.module.config;

/**
 * 某一条配置在<b>保存之前</b>的校验。实现类注册成 bean 即生效，{@link ConfigService} 的三条写入路径都会先过它。
 *
 * <p>为什么需要它：有的配置值是给别的进程解释的（比如网关的关键字规则），写错了不会在保存时报错，
 * 而是在网关那边悄悄不生效 —— 或者更糟，生效了、把真人封一天。能在保存时拒绝的，就别等到线上。
 *
 * @Date 2026-10-17
 */
public interface ConfigValueValidator {

    /** 负责哪一条配置（{@code t_config.config_key}，不区分大小写） */
    String configKey();

    /**
     * 不合格就抛 {@link solvela.exception.BusinessException}，消息会原样给到后台页面 —— 写清楚是哪一处、怎么改。
     */
    void validate(String configValue);
}
