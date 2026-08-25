package solvela.admin.module.system.job.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import solvela.base.common.code.UserErrorCode;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.RequestUser;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaBeanUtil;
import solvela.base.common.util.SolvelaCodeUtil;
import solvela.base.common.util.SolvelaCollectionUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.admin.module.system.job.api.domain.*;
import solvela.admin.module.system.job.config.SolvelaJobAutoConfiguration;
import solvela.admin.module.system.job.config.SolvelaJobConfig;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.admin.module.system.job.constant.SolvelaJobPresetEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerSourceEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerTypeEnum;
import solvela.admin.module.system.job.constant.SolvelaJobUtil;
import solvela.admin.module.system.job.core.SolvelaJobHandlerMeta;
import solvela.admin.module.system.job.core.SolvelaJobHandlerRegistry;
import solvela.admin.module.system.job.log.SolvelaJobLogCollector;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.service.DataTracerService;
import solvela.admin.module.system.job.repository.SolvelaJobDao;
import solvela.admin.module.system.job.repository.SolvelaJobLogDao;
import solvela.admin.module.system.job.repository.domain.SolvelaJobEntity;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 定时任务 接口业务管理
 *
 * @author huke
 * @date 2024/6/17 20:41
 */
@ConditionalOnBean(SolvelaJobAutoConfiguration.class)
@ConditionalOnExpression(SolvelaJobService.ADMIN_ROLE_EXPRESSION)
@Service
public class SolvelaJobService {

    /**
     * 🔴 管理接口只在 ADMIN / ALL 角色上装配，与执行侧彻底解耦
     */
    static final String ADMIN_ROLE_EXPRESSION =
            "'${solvela.job.role:ALL}'.equalsIgnoreCase('ADMIN') or '${solvela.job.role:ALL}'.equalsIgnoreCase('ALL')";

    @Resource
    private SolvelaJobDao jobDao;

    @Resource
    private SolvelaJobLogDao jobLogDao;

    @Resource
    private SolvelaJobMsgPublisher jobMsgPublisher;

    @Resource
    private SolvelaJobHandlerRegistry handlerRegistry;

    @Resource
    private SolvelaJobConfig jobConfig;

    @Resource
    private SolvelaJobLogCollector logCollector;

    /**
     * 🔴 定时任务能触发发奖与结算，改 cron / 改参数 / 启停都属高危操作，
     * 改动无痕不可接受。复用现成的数据变更追踪模块，不新建审计表
     */
    @Resource
    private DataTracerService dataTracerService;

    // ==================== 查询 ====================

    public ResponseDTO<SolvelaJobVO> queryJobInfo(Integer jobId) {
        SolvelaJobEntity jobEntity = jobDao.selectById(jobId);
        if (null == jobEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        SolvelaJobVO jobVO = SolvelaBeanUtil.copy(jobEntity, SolvelaJobVO.class);
        this.handleJobInfo(new ArrayList<>(List.of(jobVO)));
        return ResponseDTO.ok(jobVO);
    }

    public ResponseDTO<PageResult<SolvelaJobVO>> queryJob(SolvelaJobQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<SolvelaJobVO> jobList = jobDao.query(page, queryForm);
        PageResult<SolvelaJobVO> pageResult = SolvelaPageUtil.convert2PageResult(page, jobList);
        this.handleJobInfo(jobList);
        return ResponseDTO.ok(pageResult);
    }

    private void handleJobInfo(List<SolvelaJobVO> jobList) {
        if (SolvelaCollectionUtil.isEmpty(jobList)) {
            return;
        }
        List<Long> logIdList = jobList.stream().map(SolvelaJobVO::getLastExecuteLogId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, SolvelaJobLogVO> lastLogMap = Collections.emptyMap();
        if (SolvelaCollectionUtil.isNotEmpty(logIdList)) {
            lastLogMap = jobLogDao.selectBatchIds(logIdList).stream()
                    .collect(Collectors.toMap(SolvelaJobLogEntity::getLogId,
                            e -> SolvelaBeanUtil.copy(e, SolvelaJobLogVO.class)));
        }

        for (SolvelaJobVO jobVO : jobList) {
            Long lastExecuteLogId = jobVO.getLastExecuteLogId();
            if (null != lastExecuteLogId) {
                jobVO.setLastJobLog(lastLogMap.get(lastExecuteLogId));
            }
            // 🔴 handler 失联标记：这类任务永远不会执行，
            //    而在旧实现里它和正常任务长得一模一样，只能靠翻日志才知道
            Optional<SolvelaJobHandlerMeta> meta = handlerRegistry.getHandler(jobVO.getHandlerName());
            jobVO.setHandlerMissingFlag(meta.isEmpty());
            meta.ifPresent(m -> {
                jobVO.setHandlerTitle(m.title());
                jobVO.setLane(m.lane().getValue());
            });
            // 未来执行时间预览：从库里的 next_trigger_time 起算，且把 jitter 一起算进去 ——
            // 预览必须与实际一致，否则运营会以为系统在乱跑
            if (Boolean.TRUE.equals(jobVO.getEnabledFlag()) && null != jobVO.getNextTriggerTime()) {
                List<LocalDateTime> nextList = new ArrayList<>();
                nextList.add(jobVO.getNextTriggerTime());
                nextList.addAll(SolvelaJobUtil.queryNextTimeList(jobVO.getTriggerType(), jobVO.getTriggerValue(),
                        jobVO.getNextTriggerTime(), jobVO.getJobId(),
                        null == jobVO.getJitterSeconds() ? 0 : jobVO.getJitterSeconds(), 4));
                jobVO.setNextJobExecuteTimeList(nextList);
            }
        }
    }

    public ResponseDTO<PageResult<SolvelaJobLogVO>> queryJobLog(SolvelaJobLogQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<SolvelaJobLogVO> logList = jobLogDao.query(page, queryForm);
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, logList));
    }

    /**
     * 已注册的执行器列表，供后台下拉选择。
     *
     * <p>让运营从列表里<b>选</b>而不是手打 —— 手打是「配了却永远不跑」的温床
     */
    public ResponseDTO<List<SolvelaJobHandlerVO>> queryHandlerList() {
        List<SolvelaJobHandlerVO> voList = handlerRegistry.getAllHandler().stream()
                .map(meta -> {
                    SolvelaJobHandlerVO vo = new SolvelaJobHandlerVO();
                    vo.setHandlerName(meta.name());
                    vo.setTitle(meta.title());
                    vo.setGroupName(meta.group());
                    vo.setLane(meta.lane().getValue());
                    vo.setIdempotent(meta.idempotent());
                    vo.setBizDateOffset(meta.bizDateOffset());
                    vo.setDefaultTimeoutSeconds(meta.defaultTimeoutSeconds());
                    vo.setHandlerClassName(meta.handlerClassName());
                    vo.setParamSchemaList(java.util.Arrays.stream(meta.params()).map(pa -> {
                        SolvelaJobHandlerVO.ParamSchema schema = new SolvelaJobHandlerVO.ParamSchema();
                        schema.setKey(pa.key());
                        schema.setDesc(pa.desc());
                        schema.setType(pa.type().name());
                        schema.setRequired(pa.required());
                        schema.setDefaultValue(pa.defaultValue());
                        schema.setOptions(java.util.Arrays.asList(pa.options()));
                        return schema;
                    }).toList());
                    return vo;
                })
                .toList();
        return ResponseDTO.ok(voList);
    }

    /**
     * 触发时间预览：任务<b>保存之前</b>就把接下来几次的触发时刻算出来。
     *
     * <p>它补的是两个坑：
     * <ol>
     *   <li>cron 写错是静默失败 —— 合法 ≠ 符合预期。
     *       {@code 0 0 0 1 * *} 究竟是「每月 1 号」还是「每天 1 点」，看五次预览两秒就知道；</li>
     *   <li>🔴 <b>打散默认开启且原本不可见</b> —— 配「每天 2:00」实际在 {@code 02:00:37} 触发，
     *       没有预览的话这个 37 秒会被当成 bug 报上来。</li>
     * </ol>
     *
     * <p>🔴 <b>新建任务时预览是「不精确」的，而且必须如实说出来。</b>
     * 打散偏移是 {@code hash(jobId) % jitterSeconds}，而 id 要等保存后才有 ——
     * 这时只能给出未打散的基准时刻。假装算准比不给预览更糟：
     * 运营会拿着一个精确到秒的时刻去对，然后发现对不上。
     */
    public ResponseDTO<SolvelaJobTriggerPreviewVO> previewTriggerTime(SolvelaJobTriggerPreviewForm form) {
        SolvelaJobTriggerPreviewVO vo = new SolvelaJobTriggerPreviewVO();
        String triggerType = form.getTriggerType();
        String triggerValue = null == form.getTriggerValue() ? "" : form.getTriggerValue().trim();

        // 打散秒数：非 CUSTOM 档位一律取档位值，与 applyPreset 的口径保持一致 ——
        // 两处若各算各的，预览就会和实际对不上，那比没有预览更误导人
        SolvelaJobPresetEnum preset = SolvelaJobPresetEnum.resolve(form.getPresetCode());
        int jitterSeconds = preset.isCustom()
                ? (null == form.getJitterSeconds() ? 0 : form.getJitterSeconds())
                : preset.getJitterSeconds();

        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(triggerType)) {
            // 一次性任务强制不打散：那是业务时间点，不是调度时间点
            vo.setJitterSeconds(0);
            vo.setExact(true);
            try {
                LocalDateTime fireTime = LocalDateTime.parse(triggerValue.replace(' ', 'T'));
                vo.setValid(true);
                vo.setNextTimeList(List.of(fireTime));
                vo.setMessage(fireTime.isBefore(LocalDateTime.now())
                        ? "⚠️ 该时刻已过去：任务保存后会被判定为「错过调度」，按 misfire 策略处理"
                        : "一次性任务，执行后自动终结");
            } catch (Exception e) {
                vo.setValid(false);
                vo.setMessage("时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
            }
            return ResponseDTO.ok(vo);
        }

        if (!SolvelaJobTriggerTypeEnum.CRON.equalsValue(triggerType)) {
            vo.setValid(false);
            vo.setMessage("不支持的触发类型：" + triggerType);
            return ResponseDTO.ok(vo);
        }
        if (!SolvelaJobUtil.checkCron(triggerValue)) {
            vo.setValid(false);
            vo.setMessage("cron 表达式无法解析。本项目用六段式：秒 分 时 日 月 周，例如 0 15 2 * * *（每天 2:15）");
            return ResponseDTO.ok(vo);
        }

        // jobId 为空（新建）时 applyJitter 会原样返回，正好就是「未打散的基准时刻」
        List<LocalDateTime> nextList = SolvelaJobUtil.queryNextTimeList(
                triggerType, triggerValue, LocalDateTime.now(), form.getJobId(), jitterSeconds, PREVIEW_COUNT);
        if (nextList.isEmpty()) {
            // 语法合法但永远排不出下一次，例如 2 月 30 日
            vo.setValid(false);
            vo.setMessage("表达式合法，但算不出任何未来触发时刻 —— 请检查日期组合是否根本不存在");
            return ResponseDTO.ok(vo);
        }

        boolean exact = null != form.getJobId() || jitterSeconds <= 0;
        vo.setValid(true);
        vo.setJitterSeconds(jitterSeconds);
        vo.setExact(exact);
        vo.setNextTimeList(nextList);
        if (jitterSeconds > 0) {
            vo.setMessage(exact
                    ? "已含打散偏移（同一任务每次固定，用于错开整点惊群）"
                    : String.format("以上是未打散的基准时刻。实际触发会固定延后 0~%d 秒 —— "
                    + "具体偏移在保存后才确定（由任务 id 决定，同一任务每次相同）", jitterSeconds));
        }
        return ResponseDTO.ok(vo);
    }

    /**
     * 预览几次。五次足够看出周期规律，再多屏幕放不下
     */
    private static final int PREVIEW_COUNT = 5;

    // ==================== 增改 ====================

    public synchronized ResponseDTO<String> addJob(SolvelaJobAddForm addForm) {
        ResponseDTO<String> checkRes = this.checkParam(addForm);
        if (!checkRes.getOk()) {
            return checkRes;
        }
        SolvelaJobEntity jobEntity = SolvelaBeanUtil.copy(addForm, SolvelaJobEntity.class);
        jobEntity.setJobCode(SolvelaCodeUtil.generateUniqueBizCode(jobDao::existsJobCode));
        jobEntity.setAppEnv(jobConfig.getEnv());
        jobEntity.setTriggerVersion(0L);
        jobEntity.setContinuousFailCount(0);
        jobEntity.setHandlerMissingFlag(false);
        jobEntity.setTerminalFlag(false);
        jobEntity.setManualModifiedFlag(false);
        jobEntity.setSource("MANUAL");
        this.applyPreset(jobEntity, addForm);
        // 🔴 新建即可被扫到。不置的话任务会静静躺着，运营以为配好了其实永远不动
        jobEntity.setNextTriggerTime(this.resolveFirstTriggerTime(jobEntity));
        jobDao.insert(jobEntity);
        dataTracerService.insert(Long.valueOf(jobEntity.getJobId()), DataTracerTypeEnum.SOLVELA_JOB);
        this.notifyClient(jobEntity.getJobId(), addForm.getUpdateName());
        return ResponseDTO.ok();
    }

    public synchronized ResponseDTO<String> updateJob(SolvelaJobUpdateForm updateForm) {
        Integer jobId = updateForm.getJobId();
        SolvelaJobEntity exist = jobDao.selectById(jobId);
        if (null == exist) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        ResponseDTO<String> checkRes = this.checkParam(updateForm);
        if (!checkRes.getOk()) {
            return checkRes;
        }
        SolvelaJobEntity jobEntity = SolvelaBeanUtil.copy(updateForm, SolvelaJobEntity.class);
        this.applyPreset(jobEntity, updateForm);
        // 🔴 改了触发配置必须重算 next_trigger_time，否则要等到下一次原定触发后才生效 ——
        //    而改 cron 的人默认它立刻生效，这个落差极易被当成「配置没保存上」
        if (!Objects.equals(exist.getTriggerType(), updateForm.getTriggerType())
                || !Objects.equals(exist.getTriggerValue(), updateForm.getTriggerValue())) {
            jobEntity.setNextTriggerTime(this.resolveFirstTriggerTime(jobEntity));
            jobEntity.setTerminalFlag(false);
        }
        // 衍生任务被人工改过就打标，向导不再覆盖（第三档用）
        if ("SYSTEM".equals(exist.getSource())) {
            jobEntity.setManualModifiedFlag(true);
        }
        jobDao.updateById(jobEntity);
        // 留痕：改了什么、从什么改成什么，都要能查
        dataTracerService.update(Long.valueOf(jobId), DataTracerTypeEnum.SOLVELA_JOB, exist,
                jobDao.selectById(jobId));
        this.notifyClient(jobId, updateForm.getUpdateName());
        return ResponseDTO.ok();
    }

    /**
     * 把预设档位展开成具体字段。
     *
     * <p>🔴 <b>落库的是展开后的值，{@code preset_code} 只记录来源。</b>
     * 以后调整档位默认值<b>不回溯</b>已建任务 ——
     * 否则改一次预设，几十个线上任务的行为一起变，那是灾难。
     */
    private void applyPreset(SolvelaJobEntity entity, SolvelaJobAddForm form) {
        SolvelaJobPresetEnum preset = SolvelaJobPresetEnum.resolve(form.getPresetCode());
        entity.setPresetCode(preset.getValue());
        if (preset.isCustom()) {
            // 自定义：完全采用表单里的值，只做兜底
            entity.setTimeoutSeconds(null == form.getTimeoutSeconds() ? 0 : form.getTimeoutSeconds());
            entity.setRetryTimes(null == form.getRetryTimes() ? 0 : form.getRetryTimes());
            entity.setRetryInterval(null == form.getRetryInterval() ? 30 : form.getRetryInterval());
            entity.setMisfireStrategy(null == form.getMisfireStrategy() ? "SKIP" : form.getMisfireStrategy());
            entity.setMisfireThresholdSec(null == form.getMisfireThresholdSec() ? 300 : form.getMisfireThresholdSec());
            entity.setBlockStrategy(null == form.getBlockStrategy() ? "DISCARD" : form.getBlockStrategy());
            entity.setJitterSeconds(null == form.getJitterSeconds() ? 0 : form.getJitterSeconds());
        } else {
            entity.setTimeoutSeconds(preset.getTimeoutSeconds());
            entity.setRetryTimes(preset.getRetryTimes());
            entity.setRetryInterval(30);
            entity.setMisfireStrategy(preset.getMisfireStrategy().getValue());
            entity.setMisfireThresholdSec(preset.getMisfireThresholdSeconds());
            entity.setBlockStrategy(preset.getBlockStrategy().getValue());
            entity.setJitterSeconds(preset.getJitterSeconds());
        }
        // 🔴 ONE_TIME 强制不打散：那是业务时间点，不是调度时间点
        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(entity.getTriggerType())) {
            entity.setJitterSeconds(0);
        }
    }

    private LocalDateTime resolveFirstTriggerTime(SolvelaJobEntity entity) {
        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(entity.getTriggerType())) {
            // 一次性任务的 trigger_value 就是目标时刻
            return LocalDateTime.parse(entity.getTriggerValue().replace(' ', 'T'));
        }
        LocalDateTime base = LocalDateTime.now();
        LocalDateTime next = SolvelaJobUtil.nextTriggerTime(entity.getTriggerType(), entity.getTriggerValue(), base);
        return null == next ? base : next;
    }

    private ResponseDTO<String> checkParam(SolvelaJobAddForm form) {
        String triggerType = form.getTriggerType();
        String triggerValue = form.getTriggerValue();
        if (SolvelaJobTriggerTypeEnum.CRON.equalsValue(triggerType) && !SolvelaJobUtil.checkCron(triggerValue)) {
            return ResponseDTO.userErrorParam("cron表达式错误");
        }
        if (SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(triggerType)) {
            try {
                LocalDateTime.parse(triggerValue.replace(' ', 'T'));
            } catch (Exception e) {
                return ResponseDTO.userErrorParam("一次性任务的触发时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
            }
        }
        // 🔴 校验执行器走的是运行期匹配用的同一份注册表。
        //    原来这里是 Class.forName(全限定类名) —— 校验用原类名（必然通过），
        //    运行期匹配用的却是被 CGLIB 代理后的类名（必然失败），
        //    于是「保存成功 + 任务永不执行」，全程零报错
        Optional<SolvelaJobHandlerMeta> metaOpt = handlerRegistry.getHandler(form.getHandlerName());
        if (metaOpt.isEmpty()) {
            return ResponseDTO.userErrorParam("代码中不存在该执行器：" + form.getHandlerName());
        }
        SolvelaJobHandlerMeta meta = metaOpt.get();

        // 🔴 档位必须与执行器声明的车道兼容：FAST 执行器只能选轻量档，
        //    其余档位的超时都突破了快车道 30 秒的硬上限 —— 让它进来就等于毒死快车道
        SolvelaJobPresetEnum preset = SolvelaJobPresetEnum.resolve(form.getPresetCode());
        if (!preset.matchLane(meta.lane())) {
            return ResponseDTO.userErrorParam(String.format(
                    "执行器 %s 声明为 %s 车道，不能使用「%s」档位",
                    meta.name(), meta.lane().getDesc(), preset.getDesc()));
        }
        // 🔴 不幂等的执行器不许配重试：自动重试等于把一次失败变成两次副作用
        int retryTimes = preset.isCustom()
                ? (null == form.getRetryTimes() ? 0 : form.getRetryTimes())
                : preset.getRetryTimes();
        if (retryTimes > 0 && !meta.idempotent()) {
            return ResponseDTO.userErrorParam(String.format(
                    "执行器 %s 未声明幂等，不能配置失败重试（重试会让一次失败变成两次副作用）", meta.name()));
        }
        return ResponseDTO.ok();
    }

    public ResponseDTO<String> updateJobEnabled(SolvelaJobEnabledUpdateForm updateForm) {
        Integer jobId = updateForm.getJobId();
        SolvelaJobEntity exist = jobDao.selectById(jobId);
        if (null == exist) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        Boolean enabledFlag = updateForm.getEnabledFlag();
        if (Objects.equals(enabledFlag, exist.getEnabledFlag())) {
            return ResponseDTO.ok();
        }
        SolvelaJobEntity jobEntity = new SolvelaJobEntity();
        jobEntity.setJobId(jobId);
        jobEntity.setEnabledFlag(enabledFlag);
        jobEntity.setUpdateName(updateForm.getUpdateName());
        // 停用期间时间轮不再推进，重新启用时按当前时间重排 ——
        // 否则会带着一个陈旧的 next_trigger_time 立刻被判成 misfire
        if (Boolean.TRUE.equals(enabledFlag)) {
            jobEntity.setNextTriggerTime(this.resolveFirstTriggerTime(exist));
        }
        jobDao.updateById(jobEntity);
        dataTracerService.addTrace(Long.valueOf(jobId), DataTracerTypeEnum.SOLVELA_JOB,
                Boolean.TRUE.equals(enabledFlag) ? "启用任务" : "停用任务");
        this.notifyClient(jobId, updateForm.getUpdateName());
        return ResponseDTO.ok();
    }

    public synchronized ResponseDTO<String> deleteJob(Integer jobId, RequestUser requestUser) {
        jobDao.updateDeletedFlag(jobId, Boolean.TRUE);
        dataTracerService.delete(Long.valueOf(jobId), DataTracerTypeEnum.SOLVELA_JOB);
        this.notifyClient(jobId, requestUser.getUserName());
        return ResponseDTO.ok();
    }

    // ==================== 手动执行与重跑 ====================

    /**
     * 立即执行。
     *
     * <p>🔴 <b>不再直接跑，而是写一条 PENDING 日志让扫描线程捞走。</b>
     * 这样手动与定时走同一套抢占 —— 天然互斥，不会再出现
     * 「运营点一下撞上自动调度，同一任务并发跑两遍」的资损场景。
     *
     * <p>保留「忽略任务开启状态」的既有语义：运营需要这个能力来调试未启用的任务。
     */
    public ResponseDTO<String> execute(SolvelaJobExecuteForm executeForm) {
        SolvelaJobEntity job = jobDao.selectById(executeForm.getJobId());
        if (null == job) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        if (Boolean.TRUE.equals(job.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("任务已删除");
        }
        Optional<SolvelaJobHandlerMeta> meta = handlerRegistry.getHandler(job.getHandlerName());
        if (meta.isEmpty()) {
            return ResponseDTO.userErrorParam("代码中不存在该执行器：" + job.getHandlerName());
        }
        String param = (null == executeForm.getParam() || executeForm.getParam().isBlank())
                ? job.getParam() : executeForm.getParam();
        LocalDate bizDate = null != executeForm.getBizDate() ? executeForm.getBizDate()
                : LocalDate.now().plusDays(meta.get().bizDateOffset());
        return this.submitPending(job, param, bizDate, executeForm.getUpdateName(), 0, null);
    }

    /**
     * 一键重跑：用<b>当初那一次</b>的参数与业务日期重跑。
     *
     * <p>🔴 关键在「当初那一次」：拿当前配置重跑是错的 —— 配置可能早已被人改过，
     * 而运营的意图是「把那次失败的补回来」，不是「用新配置跑一遍」。
     */
    public ResponseDTO<String> rerun(Long logId, String operator) {
        SolvelaJobLogEntity sourceLog = jobLogDao.selectById(logId);
        if (null == sourceLog) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        SolvelaJobEntity job = jobDao.selectById(sourceLog.getJobId());
        if (null == job || Boolean.TRUE.equals(job.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("任务已删除，无法重跑");
        }
        if (!handlerRegistry.contains(job.getHandlerName())) {
            return ResponseDTO.userErrorParam("代码中不存在该执行器：" + job.getHandlerName());
        }
        return this.submitPending(job, sourceLog.getParamSnapshot(), sourceLog.getBizDate(),
                operator, 0, sourceLog.getLogId());
    }

    /**
     * 写一条 PENDING 记录，等扫描线程捞走。
     */
    private ResponseDTO<String> submitPending(SolvelaJobEntity job, String param, LocalDate bizDate,
                                              String operator, int retrySeq, Long retryOfLogId) {
        LocalDateTime now = LocalDateTime.now();
        SolvelaJobLogEntity logEntity = new SolvelaJobLogEntity();
        logEntity.setJobId(job.getJobId());
        logEntity.setJobName(job.getJobName());
        logEntity.setAppEnv(job.getAppEnv());
        logEntity.setTriggerSource(SolvelaJobTriggerSourceEnum.MANUAL.getValue());
        // MANUAL 记录的 trigger_time 是「点击时刻」，与 SCHEDULE 的「原定调度时刻」不是一个概念，
        // 靠 uk_job_trigger 里的 trigger_source 把两者隔离开
        logEntity.setTriggerTime(now.withNano(0));
        logEntity.setRetrySeq(retrySeq);
        logEntity.setBizDate(bizDate);
        logEntity.setParamSnapshot(param);
        logEntity.setRetryOfLogId(retryOfLogId);
        logEntity.setStatus(SolvelaJobExecuteStatusEnum.PENDING.getValue());
        // 🔴 只有 PENDING 记录带 fire_time；立即执行即 now
        logEntity.setFireTime(now);
        logEntity.setCreateName(operator);
        // ⚠️ execute_start_time / ip / process_id / program_path 一律留 NULL：
        //    这条记录还没开始执行，也还不知道会落到哪个节点上。
        //    v3.59.0 之前这里填的是 now 和 '-' 三个占位值 —— 那是在数据里写谎话，
        //    列表页会给一条根本没跑的记录显示「开始时间」。
        //    真值由扫描线程在抢占时补上（preemptPendingLog + fillNodeInfo）。
        logEntity.setExecuteTimeMillis(0L);

        try {
            jobLogDao.insert(logEntity);
        } catch (DuplicateKeyException e) {
            // 同一秒内连点两次会撞 uk_job_trigger。这是防重特性，但必须转译成人话 ——
            // 直接把 SQL 异常抛给运营是铁律 8 明确反对的
            return ResponseDTO.userErrorParam("该任务 1 秒内已被触发，请勿重复点击");
        }
        // pub/sub 只是加速：丢了最多晚一秒被扫到，不影响正确性
        this.notifyClient(job.getJobId(), operator);
        return ResponseDTO.ok();
    }

    /**
     * 终止正在执行的任务。
     *
     * <p>🔴 <b>界面必须如实说「已发出中断信号」而不是「已终止」。</b>
     * {@code cancel(true)} 本质是 {@link Thread#interrupt()} ——
     * 不响应中断的执行器砍不掉，谎报成功会让运营以为处理完了，
     * 转身去做别的，而任务还在跑。
     */
    public ResponseDTO<String> terminate(Long logId, String operator) {
        SolvelaJobLogEntity logEntity = jobLogDao.selectById(logId);
        if (null == logEntity) {
            return ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST);
        }
        if (!Objects.equals(logEntity.getStatus(), SolvelaJobExecuteStatusEnum.RUNNING.getValue())) {
            return ResponseDTO.userErrorParam("该执行记录不在「执行中」状态，无需终止");
        }
        SolvelaJobMsg msg = new SolvelaJobMsg();
        msg.setJobId(logEntity.getJobId());
        msg.setLogId(logId);
        msg.setMsgType(SolvelaJobMsg.MsgTypeEnum.TERMINATE_JOB);
        msg.setUpdateName(operator);
        // 广播出去：只有真正持有那个 Future 的节点会响应，其余节点安静忽略
        jobMsgPublisher.publishToClient(msg);
        dataTracerService.addTrace(Long.valueOf(logEntity.getJobId()), DataTracerTypeEnum.SOLVELA_JOB,
                "终止执行记录 logId=" + logId);
        return ResponseDTO.ok("已发出中断信号。能否真正停止取决于该执行器是否响应中断，请稍后刷新状态确认");
    }

    /**
     * 查看某次执行的日志。
     *
     * <p>取不到就是取不到（已过期、或那台节点当时没开采集）—— 如实返回空，
     * 不要伪造成「无日志输出」，那会让人以为任务真的什么都没打
     */
    public ResponseDTO<List<String>> queryExecuteLog(Long logId) {
        return ResponseDTO.ok(logCollector.read(logId));
    }

    private void notifyClient(Integer jobId, String operator) {
        SolvelaJobMsg jobMsg = new SolvelaJobMsg();
        jobMsg.setJobId(jobId);
        jobMsg.setMsgType(SolvelaJobMsg.MsgTypeEnum.UPDATE_JOB);
        jobMsg.setUpdateName(operator);
        jobMsgPublisher.publishToClient(jobMsg);
    }
}
