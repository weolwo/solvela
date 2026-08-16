package sa.base.module.support.job.core;

import lombok.extern.slf4j.Slf4j;
import sa.base.module.support.job.repository.SmartJobDao;
import sa.base.module.support.job.repository.domain.SmartJobEntity;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 定时任务执行器注册表：<b>handler_name → 实现</b> 的唯一真源。
 *
 * <p>做两件事：
 * <ol>
 *   <li><b>注册</b>：扫描所有 {@link SmartJob} Bean，读 {@link SmartJobHandler} 注解建表；</li>
 *   <li><b>对账</b>：启动时与库里的任务配置双向核对，把「配了但没有」和「有但没配」都喊出来。</li>
 * </ol>
 *
 * <p>🔴 <b>对账比换注册方式本身更重要。</b> 原实现匹配不上时是一句 {@code continue} ——
 * 不报错、不打日志、任务从此不跑，而后台看起来一切正常。
 * 这正是交接文档 §0 铁律 16 说的那种「通过和空过分不出来」的形状：
 * 出问题时你甚至不知道该去哪儿看。
 *
 * <p>ADMIN 角色也会装配本类（只读用途）：新增任务时校验 handler_name 真实存在、
 * 以及给前端下拉框提供候选列表。所以本类<b>不依赖任何 WORKER 侧组件</b>。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobHandlerRegistry implements SmartInitializingSingleton {

    private final SmartJobDao jobDao;

    /**
     * 是否执行启动对账。ADMIN 与 WORKER 都在同一进程时只需对账一次，
     * 但两者分开部署时都该各自对一次 —— 因为将来它们的 classpath 可能不同。
     */
    private final boolean reconcileOnStartup;

    /**
     * handler_name → meta。用 LinkedHashMap 保持注册顺序，让启动日志和下拉框顺序稳定
     */
    private final Map<String, SmartJobHandlerMeta> handlerMap = new LinkedHashMap<>();

    private final ObjectProvider<SmartJob> jobBeanProvider;
    private final boolean isNoneRole; // 新增

    // 2. 构造函数直接接收
    public SmartJobHandlerRegistry(ObjectProvider<SmartJob> jobBeanProvider,
                                   SmartJobDao jobDao,
                                   boolean reconcileOnStartup,
                                   boolean isNoneRole) {
        this.jobBeanProvider = jobBeanProvider;
        this.jobDao = jobDao;
        this.reconcileOnStartup = reconcileOnStartup;
        this.isNoneRole = isNoneRole;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.register();
        if (reconcileOnStartup) {
            this.reconcile();
        }
    }

    /**
     * 扫描并注册。
     *
     * <p>🔴 用 {@link AopUtils#getTargetClass} 而不是 {@code bean.getClass()}：
     * 后者在 Bean 被 CGLIB 代理时拿到的是 {@code Xxx$$SpringCGLIB$$0}，
     * 注解虽然多数情况能被 {@code AnnotationUtils} 顺着父类找到，
     * 但真实类名一旦进了日志就完全没法排查。<b>这一步是整条链路对代理免疫的关键。</b>
     */
    private void register() {
        // 3. 🔴 关键点：在所有 Bean 都加载完毕后，再去兑现 ObjectProvider！
        List<SmartJob> jobBeanList = isNoneRole
                ? List.of()
                : jobBeanProvider.orderedStream().toList();
        for (SmartJob bean : jobBeanList) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            SmartJobHandler anno = AnnotationUtils.findAnnotation(targetClass, SmartJobHandler.class);
            if (null == anno) {
                // 没打注解的实现类：不是错误，但它永远不可能被调度到，必须说出来
                log.warn("==== SmartJob ==== 实现了 SmartJob 但未标注 @SmartJobHandler，将永远不会被调度：{}",
                        targetClass.getName());
                continue;
            }
            String name = anno.name();
            if (null == name || name.isBlank()) {
                throw new IllegalStateException("@SmartJobHandler.name 不能为空：" + targetClass.getName());
            }
            SmartJobHandlerMeta exist = handlerMap.get(name);
            if (null != exist) {
                // 🔴 重名直接让启动失败。静默取其一是最坏的结果：
                //    两个执行器抢同一个名字，跑起来的是哪个取决于 Bean 扫描顺序，
                //    换台机器、换个 JDK 版本都可能变 —— 属于最难复现的一类故障。
                throw new IllegalStateException(String.format(
                        "@SmartJobHandler 名称重复：name=%s 同时被 %s 和 %s 使用",
                        name, exist.handlerClassName(), targetClass.getName()));
            }
            handlerMap.put(name, new SmartJobHandlerMeta(
                    name, anno.title(), anno.group(), anno.lane(), anno.idempotent(),
                    anno.bizDateOffset(), anno.defaultTimeoutSeconds(), anno.params(),
                    targetClass.getName(), bean));
        }
        log.info("==== SmartJob ==== handler 注册完毕，共 {} 个：{}", handlerMap.size(), handlerMap.keySet());
    }

    /**
     * 启动对账：把「库里配了但代码没有」和「代码有但库里没配」都摆到台面上。
     *
     * <p>前者会被标记 {@code handler_missing_flag = 1}（本档先只落日志与内存判断，
     * 执行侧靠 {@link #getHandler} 返回空来兜住），后者只是提示。
     */
    private void reconcile() {
        List<SmartJobEntity> dbJobList;
        try {
            dbJobList = jobDao.selectList(null);
        } catch (Exception e) {
            // 对账失败不能拖垮启动 —— 表还没建、库还没连上都可能走到这里
            log.error("==== SmartJob ==== 启动对账失败，跳过（不影响启动）", e);
            return;
        }

        Set<String> dbNames = dbJobList.stream()
                .filter(e -> !Boolean.TRUE.equals(e.getDeletedFlag()))
                .map(SmartJobEntity::getHandlerName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // ① 库里配了、代码没有 —— 这些任务永远跑不起来，必须 ERROR
        List<SmartJobEntity> missing = dbJobList.stream()
                .filter(e -> !Boolean.TRUE.equals(e.getDeletedFlag()))
                .filter(e -> null == e.getHandlerName() || !handlerMap.containsKey(e.getHandlerName()))
                .toList();
        if (!missing.isEmpty()) {
            String detail = missing.stream()
                    .map(e -> e.getJobName() + "(handler=" + e.getHandlerName() + ")")
                    .collect(Collectors.joining(", "));
            log.error("==== SmartJob ==== 🔴 有 {} 个任务的 handler 在代码中不存在，它们不会被执行：{}",
                    missing.size(), detail);
        }

        // ② 代码有、库里没配 —— 只是还没挂上，提示即可
        List<String> unmounted = handlerMap.keySet().stream()
                .filter(name -> !dbNames.contains(name))
                .toList();
        if (!unmounted.isEmpty()) {
            log.warn("==== SmartJob ==== 有 {} 个 handler 尚未挂载任务（可在后台新增）：{}",
                    unmounted.size(), unmounted);
        }

        if (missing.isEmpty() && unmounted.isEmpty()) {
            log.info("==== SmartJob ==== 启动对账通过，配置与代码完全一致");
        }
    }

    /**
     * 按名字取执行器。取不到返回空 —— 调用方必须显式处理，不允许静默跳过
     */
    public Optional<SmartJobHandlerMeta> getHandler(String handlerName) {
        if (null == handlerName || handlerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlerMap.get(handlerName));
    }

    public boolean contains(String handlerName) {
        return this.getHandler(handlerName).isPresent();
    }

    /**
     * 全部已注册的执行器，供后台下拉框与「未挂载」列表使用
     */
    public Collection<SmartJobHandlerMeta> getAllHandler() {
        return handlerMap.values();
    }
}
