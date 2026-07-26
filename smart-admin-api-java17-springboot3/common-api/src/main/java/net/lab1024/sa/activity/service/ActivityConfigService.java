package net.lab1024.sa.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.activity.dao.ActivityConfigDao;
import net.lab1024.sa.activity.domain.entity.ActivityConfig;
import net.lab1024.sa.activity.domain.form.ActivityConfigAddForm;
import net.lab1024.sa.activity.domain.form.ActivityConfigQueryForm;
import net.lab1024.sa.activity.domain.form.ActivityConfigUpdateForm;
import net.lab1024.sa.activity.domain.vo.ActivityConfigVO;
import net.lab1024.sa.activity.manager.ActivityConfigManager;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class ActivityConfigService {

    private final ActivityConfigDao activityConfigDao;
    private final ActivityConfigManager activityConfigManager;

    /**
     * 按活动编码查询（奖池工作台保存时判定活动是否上线等场景使用）
     */
    public ActivityConfig getByActivityCode(String activityCode) {
        return activityConfigManager.lambdaQuery().eq(ActivityConfig::getActivityCode, activityCode).one();
    }

    /**
     * 分页查询
     */
    public PageResult<ActivityConfigVO> queryPage(ActivityConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ActivityConfigVO> list = activityConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(ActivityConfigAddForm addForm) {
        ActivityConfig activityConfig = SmartBeanUtil.copy(addForm, ActivityConfig.class);
        activityConfigDao.insert(activityConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(ActivityConfigUpdateForm updateForm) {
        ActivityConfig activityConfig = SmartBeanUtil.copy(updateForm, ActivityConfig.class);
        activityConfigDao.updateById(activityConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        activityConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        activityConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
