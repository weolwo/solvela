package sa.activity.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import sa.activity.dao.ActivityDisplayDao;
import sa.activity.domain.entity.ActivityConfig;
import sa.activity.domain.entity.ActivityDisplay;
import sa.base.common.domain.RequestUser;
import sa.base.common.exception.BusinessException;
import sa.base.module.support.file.service.FileAssetService;
import sa.base.module.support.file.service.RichTextImageExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 活动 C 端展示配置。
 *
 * @Date 2026-08-10
 */
@Slf4j
@Service
public class ActivityDisplayService {

    /**
     * 引用关系里的业务类型标识。写死成常量而不是散在各处的字面量 ——
     * 拼错一个字母不会报错，只会让引用悄悄记到一个不存在的业务上，然后图被清理任务删掉。
     */
    public static final String BIZ_TYPE = "ACTIVITY_DISPLAY";

    /**
     * 富文本长度上限。{@code mediumtext} 能装 16MB，但那不该是业务上限 ——
     * 规则说明写到 200KB 已经不正常了，多半是有人把 base64 图片粘进来了。
     */
    private static final int MAX_RULE_CONTENT_LENGTH = 200_000;

    @Resource
    private ActivityDisplayDao activityDisplayDao;

    @Resource
    private FileAssetService fileAssetService;

    @Resource
    private ActivityConfigService activityConfigService;

    public ActivityDisplay getByActivityId(Long activityId) {
        return activityDisplayDao.getByActivityId(activityId);
    }

    /**
     * 按活动编码查。
     *
     * <p><b>对外一律用 activityCode 寻址，不用 activityId</b>：整个活动域都是这么做的 ——
     * 向导返回的是 code、路由参数是 code、玩法配置的保存接口收的也是 code。
     * 而 {@code wizardCreate} 压根不返回 id，前端拿不到。
     *
     * <p>存储层仍然 FK 到 {@code activity_id}（数值外键更小、索引更省），
     * code → id 的换算在这一层做掉。这是「API 用业务键、存储用代理键」的常规分工。
     */
    public ActivityDisplay getByActivityCode(String activityCode) {
        ActivityConfig activity = requireActivity(activityCode);
        return activityDisplayDao.getByActivityId(activity.getId());
    }

    /**
     * 按活动编码保存。活动 ID 从活动上带过来，前端不用传也篡改不了。
     */
    @Transactional(rollbackFor = Exception.class)
    public ActivityDisplay saveByCode(String activityCode, ActivityDisplay form, RequestUser user) {
        ActivityConfig activity = requireActivity(activityCode);
        form.setActivityId(activity.getId());
        return save(form, user);
    }

    private ActivityConfig requireActivity(String activityCode) {
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            throw new BusinessException("活动不存在：" + activityCode);
        }
        return activity;
    }

    /**
     * 保存展示配置，并把它引用到的<b>全部</b>图片登记进 {@code t_file_relation}。
     *
     * <p><b>登记引用不是可选步骤</b>：不登记的话，孤儿清理任务会把这些图当成"上传了但没人引用"
     * 的垃圾删掉。表现是活动上线三个月后主视觉和规则配图突然全变叉，而且查不出是谁删的 ——
     * 因为是定时任务按规则删的，日志里只有一句"清理了 N 个过期临时文件"。
     */
    @Transactional(rollbackFor = Exception.class)
    public ActivityDisplay save(ActivityDisplay form, RequestUser user) {
        if (form.getActivityId() == null) {
            throw new BusinessException("活动ID不能为空");
        }
        checkRuleContent(form.getRuleContent());

        String operator = user == null ? null : user.getUserName();
        ActivityDisplay existing = activityDisplayDao.getByActivityId(form.getActivityId());
        if (existing == null) {
            form.setId(null);
            form.setCreateBy(operator);
            form.setUpdateBy(operator);
            activityDisplayDao.insert(form);
        } else {
            form.setId(existing.getId());
            form.setUpdateBy(operator);
            activityDisplayDao.updateById(form);
        }

        // confirm 内部是「先清后建」，所以这里传的是本次保存后的完整引用集合 ——
        // 上一版引用了、这一版删掉的图会自动解除引用，不会越攒越多
        fileAssetService.confirm(collectReferencedFileIds(form), BIZ_TYPE, form.getActivityId());
        return form;
    }

    /**
     * 活动被删除时解除引用。<b>只解除关系不删文件</b> —— 同一张主视觉可能被多个活动复用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseFiles(Long activityId) {
        fileAssetService.releaseRelation(BIZ_TYPE, activityId);
    }

    // ------------------------------------------------------------------

    /**
     * 本次配置引用到的全部 fileId：三个独立图片字段 + 富文本正文里的图。
     */
    private List<Long> collectReferencedFileIds(ActivityDisplay form) {
        Set<Long> ids = new LinkedHashSet<>();
        addIfPresent(ids, form.getMainImageId());
        addIfPresent(ids, form.getBgImageId());
        addIfPresent(ids, form.getShareImageId());

        Set<String> sources = RichTextImageExtractor.extractImageSources(form.getRuleContent());
        if (!sources.isEmpty()) {
            List<Long> resolved = fileAssetService.resolveFileIds(sources);
            ids.addAll(resolved);
            if (resolved.size() < sources.size()) {
                // 外链图片、已删除的文件都会走到这里，属正常情况；但数量对不上时留一条线索，
                // 否则"图后来不见了"这类问题完全无从查起
                log.info("[ActivityDisplay] 活动 {} 的规则正文有 {} 张图，其中 {} 张能对应到素材库",
                        form.getActivityId(), sources.size(), resolved.size());
            }
        }
        return new ArrayList<>(ids);
    }

    private static void addIfPresent(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private static void checkRuleContent(String ruleContent) {
        if (ruleContent == null) {
            return;
        }
        // 前端编辑器那边也会关掉 base64，但那个配置是会被人改回来的，后端这道才是真的。
        // 一张图就是几百 KB 的 base64 塞进 mediumtext，几十个活动就把表撑爆，
        // 将来推 CDN 的 JSON 也会大到离谱
        if (RichTextImageExtractor.containsBase64Image(ruleContent)) {
            throw new BusinessException("活动规则不允许内嵌 base64 图片，请通过素材库上传后引用");
        }
        if (ruleContent.length() > MAX_RULE_CONTENT_LENGTH) {
            throw new BusinessException("活动规则内容过长（" + ruleContent.length()
                    + " 字符），上限 " + MAX_RULE_CONTENT_LENGTH);
        }
    }
}
