package solvela.admin.module.system.operatelog.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import solvela.base.constant.StringConst;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.code.SystemErrorCode;
import solvela.exception.BusinessException;
import solvela.base.util.SolvelaIpUtil;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.system.operatelog.OperateLogDao;
import solvela.admin.module.system.operatelog.domain.OperateLogEntity;
import solvela.base.json.JsonUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 后台操作日志：切 {@code solvela.admin} 下所有 Controller 的<b>写操作</b>，落 t_operate_log。
 *
 * <h3>🔴 2026-08-25：切点从「认注解」改成「认包路径」</h3>
 * 原先的切点是
 * {@code @within(...OperateLog) || @annotation(...OperateLog)} —— 只切标了
 * {@code @OperateLog} 的类或方法。问题是<b>全项目只有 Solvela 自带的那几个演示
 * Controller（goods / OA 通知 / OA 企业）标过它</b>，自建的会员、钱包、活动、抽奖、
 * 彩票、任务一个都没标。于是这套日志实际只记录了演示模块的操作；等演示模块被删掉，
 * 它就彻底停止工作了 —— 实测 t_operate_log 最后一条停在 2026-08-23，url 全是 /oa/*。
 *
 * <p>这种「装好了但没接线」的东西比没有更危险：后台有个「操作日志」菜单，点进去有数据，
 * 谁都以为审计是有的，直到出事去查才发现最近半年一条都没记。
 *
 * <p>改成按包路径切之后，<b>新写的 Controller 自动纳入审计</b>，不依赖任何人记得加注解。
 * 日志文案沿用方法上的 Swagger {@code @Operation(summary)}、模块名沿用类上的 {@code @Tag(name)} ——
 * 这两个注解本来就全项目铺满了（写文档时顺手写的），一行没白写。
 *
 * <h3>只记写操作</h3>
 * 查询接口不记，否则后台随便点几下菜单就刷进几十条，真正的写操作反而被淹没。
 * 判据是方法名前缀白名单（{@link #READ_METHOD_PREFIXES}）—— 用<b>白名单</b>而不是黑名单：
 * 前缀不认识的方法一律记下来。审计宁可多记，不可漏记。
 *
 * <p>⚠️ 刻意<b>不</b>把 export / download 当读操作：批量导出数据恰恰是最该留痕的行为。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2021-12-08 20:48:52
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Aspect
public abstract class OperateLogAspect {

    /**
     * 只切管理端。C 端（solvela-app）不装配本切面，它那边的行为审计是另一套。
     */
    private static final String POINT_CUT = "execution(* solvela.admin..*Controller.*(..))";

    /**
     * 读操作的方法名前缀，命中则不记日志。
     *
     * <p>取值来自对现有 Controller 方法名的实际统计，不是拍脑袋列的。
     * 🔴 新增前缀前先想清楚：加错一个前缀 = 那一类写操作从此无声无息。
     */
    private static final String[] READ_METHOD_PREFIXES = {
            "query", "get", "page", "detail", "stat", "funnel", "list", "preview", "check", "view"
    };

    /**
     * 请求参数里需要脱敏的字段名（不区分大小写），见 {@link #maskSensitive}。
     */
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "\"((?i)password|newPassword|oldPassword|loginPwd|pwd|secretKey|accessKey|token)\"\\s*:\\s*\"[^\"]*\"");

    @Resource
    private ApplicationContext applicationContext;
    /**
     * 线程池
     */
    private ThreadPoolTaskExecutor taskExecutor;

    public abstract OperateLogConfig getOperateLogConfig();

    public OperateLogAspect() {
        this.initThread();
    }

    @Pointcut(POINT_CUT)
    public void logPointCut() {
    }

    @AfterReturning(pointcut = "logPointCut()")
    public void doAfterReturning(JoinPoint joinPoint) {
        handleLog(joinPoint, null);
    }

    @AfterThrowing(value = "logPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e);
    }

    /**
     * 初始化线程池
     */
    private void initThread() {
        OperateLogConfig config = getOperateLogConfig();
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        if (null != config.getCorePoolSize()) {
            corePoolSize = config.getCorePoolSize();
        }
        taskExecutor = new ThreadPoolTaskExecutor();
        // 设置核心线程数
        taskExecutor.setCorePoolSize(corePoolSize);
        // 设置最大线程数
        taskExecutor.setMaxPoolSize(corePoolSize * 2);
        // 设置队列容量
        taskExecutor.setQueueCapacity(1000);
        // 设置线程活跃时间（秒）
        taskExecutor.setKeepAliveSeconds(60);
        // 设置默认线程名称
        taskExecutor.setThreadNamePrefix("solvela-operate-log");
        // 设置拒绝策略
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);

        // 🔴 initialize() 必须放在<b>所有 setter 之后</b>。
        //    队列是在 initialize() 里按当时的 queueCapacity 建出来的，之后再调
        //    setQueueCapacity 只改字段、不换队列 —— 原来的写法把 initialize 放在最前面，
        //    结果队列一直是默认的<b>无界</b> LinkedBlockingQueue。
        //    无界队列还有个连带后果：永远不会触发拒绝策略，下面那个 CallerRunsPolicy
        //    等于没配，操作日志积压时不会给调用方施加背压，只会一路涨到 OOM。
        //    （setCorePoolSize 之类会转发给已建好的池，所以只有队列这一项静默失效，更难发现。）
        taskExecutor.initialize();
    }

    protected void handleLog(final JoinPoint joinPoint, final Exception e) {
        try {
            if (isReadOperation(joinPoint.getSignature().getName())) {
                return;
            }
            this.submitLog(joinPoint, e);
        } catch (Exception exp) {
            log.error("保存操作日志异常:{}", exp.getMessage());
        }
    }

    /**
     * 方法名是否属于读操作（不记日志）。
     *
     * <p>只比前缀，且要求前缀后面接的是<b>大写字母或方法结束</b> ——
     * 否则 {@code queue*}、{@code checkout*} 这类词会被 {@code "que"}/{@code "check"}
     * 之类的前缀误伤。这里的前缀都是完整单词，加这一条是为了以后有人往列表里
     * 塞短前缀时不至于悄悄放过一批写操作。
     */
    private boolean isReadOperation(String methodName) {
        for (String prefix : READ_METHOD_PREFIXES) {
            if (!methodName.startsWith(prefix)) {
                continue;
            }
            if (methodName.length() == prefix.length()) {
                return true;
            }
            if (Character.isUpperCase(methodName.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * swagger tag
     *
     * @param joinPoint
     * @return
     * @throws Exception
     */
    private Tag getApi(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();
        Tag classAnnotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), Tag.class);
        if (method != null) {
            return classAnnotation;
        }
        return null;
    }

    /**
     * swagger ApiOperation
     *
     * @param joinPoint
     * @return
     * @throws Exception
     */
    private Operation getApiOperation(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        if (method != null) {
            return method.getAnnotation(Operation.class);
        }
        return null;
    }

    /**
     * 提交存储操作日志
     *
     */
    private void submitLog(final JoinPoint joinPoint, final Throwable e) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        //设置用户信息
        RequestEmployee user = CurrentEmployee.orNull();
        if (user == null) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        String params = buildParamString(args);
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        String operateMethod = className + "." + methodName;
        String failReason = null;
        boolean successFlag = true;
        if (e != null) {
            successFlag = false;
            failReason = getExceptionString(e);
        }

        OperateLogEntity operateLogEntity =
                OperateLogEntity.builder()
                        .operateUserId(user.getUserId())
                        .operateUserType(user.getUserType())
                        .operateUserName(user.getUserName())
                        .url(request.getRequestURI())
                        .method(operateMethod)
                        .param(params)
                        .ip(user.getIp())
                        .ipRegion(SolvelaIpUtil.getRegion(user.getIp()))
                        .userAgent(user.getUserAgent())
                        .failReason(failReason)
                        .successFlag(successFlag).build();

        Operation apiOperation = this.getApiOperation(joinPoint);
        if (apiOperation != null) {
            operateLogEntity.setContent(apiOperation.summary());
        }

        Tag api = this.getApi(joinPoint);
        if (api != null) {
            String name = api.name();
            operateLogEntity.setModule(name);
        }

        /*
         * 结果只记「失败长什么样」，成功不记。
         *
         * 上一版把整个 ResponseDTO（去掉 data）序列化进 response 列。信封没了之后，
         * 成功的返回值就是业务数据本身 —— 那是<b>不能往审计日志里塞</b>的东西：
         * 会员列表、钱包余额、手机号都会成段落库，而 t_operate_log 谁都能查。
         *
         * 失败信息从异常本身推导，<b>不从响应里取</b>：本切面包的是 Controller 方法，
         * 它抛出来的时候 GlobalExceptionHandler 还没跑过 —— 那一步发生在 DispatcherServlet 里，
         * 比这里晚。想从 request 上取处理器写下的结果，取到的永远是 null。
         */
        if (e != null) {
            operateLogEntity.setResponse(JsonUtils.toJson(describeFailure(e)));
        }

        taskExecutor.execute(() -> {
            this.saveLog(operateLogEntity);
        });
    }

    /**
     * 失败结果的落库形态：{@code {"code": "...", "message": "..."}}。
     *
     * <p>与响应体里的 {@code ApiErrorResponse} 保持同样的两个字段，
     * 这样运营在操作日志里看到的 code 和用户当时看到的是同一个。
     */
    private Map<String, String> describeFailure(Throwable e) {
        if (e instanceof BusinessException be) {
            return Map.of("code", be.getErrorCode().name(),
                    "message", be.getMessage() == null ? be.getErrorCode().getMsg() : be.getMessage());
        }
        // 非业务异常一律记成系统错误：具体堆栈已经在 failReason 里，这里只标类别
        return Map.of("code", SystemErrorCode.SYSTEM_ERROR.name(),
                "message", SystemErrorCode.SYSTEM_ERROR.getMsg());
    }

    private String buildParamString(Object[] args) {
        if (args == null || args.length == 0) {
            return StringConst.EMPTY;
        }

        List<Object> filterArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof ModelAndView
                    || arg instanceof MultipartFile
                    || arg instanceof BindResult) {
                continue;
            }
            filterArgs.add(arg);
        }
        return maskSensitive(JsonUtils.toJson(filterArgs));
    }

    /**
     * 把请求参数里的敏感字段值替换成 ******。
     *
     * <p>🔴 这一步在切点改成「切全部写操作」之后<b>成了必需品</b>：原先只有几个演示接口
     * 会走到这里，现在改密码、重置密码、员工新增（带初始密码）全都会把整个请求体
     * 序列化落库 —— 等于把明文密码抄进了一张运营能在后台翻页查看的表。
     *
     * <p>用正则在 JSON 串上做替换，而不是在对象上做：参数类型五花八门（Form / String /
     * Map / 路径变量），逐类型处理写不完，而所有类型最终都要过 {@code JsonUtils.toJson}，
     * 在出口处统一拦一道最省事也最不容易漏。
     *
     * <p>⚠️ 代价是它认的是<b>字段名</b>。新增敏感字段（比如将来的 {@code idCard}）
     * 记得往这里加，否则它会安静地进日志。
     */
    private String maskSensitive(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        return SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("\"$1\":\"******\"");
    }


    private String getExceptionString(Throwable e) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw);) {
            e.printStackTrace(pw);
        }
        return sw.toString();
    }

    /**
     * 保存操作日志
     *
     * @param operateLogEntity
     * @return
     */
    private Boolean saveLog(OperateLogEntity operateLogEntity) {
        OperateLogConfig operateLogConfig = getOperateLogConfig();
        if (operateLogConfig.getSaveFunction() == null) {
            BaseMapper mapper = applicationContext.getBean(OperateLogDao.class);
            if (mapper == null) {
                return false;
            }
            mapper.insert(operateLogEntity);
            return true;
        }
        return operateLogConfig.getSaveFunction().apply(operateLogEntity);
    }

}
