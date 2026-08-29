package solvela.base.module.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.code.UserErrorCode;
import solvela.base.domain.PageResult;
import solvela.exception.BusinessException;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.base.module.config.domain.*;
import solvela.base.module.config.domain.*;
import solvela.base.json.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置业务类
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-14 20:46:27
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class ConfigService {

    /**
     * 一个简单的系统配置缓存
     */
    private final ConcurrentHashMap<String, ConfigEntity> CONFIG_CACHE = new ConcurrentHashMap<>();

    @Resource
    private ConfigDao configDao;

    /*
     * 这里原先有一个 @SolvelaReload(ReloadConst.CONFIG_RELOAD) 的 configReload()，
     * 由 support/reload 那套 300 秒轮询的热加载框架定时调用，做全量重载。
     * 随 reload 模块一起删除（2026-08-25）。
     *
     * 删它是安全的：add / updateConfig / updateValueByKey 三条写入路径
     * <b>本来就</b>各自调用了 refreshConfigCache(configId)，配置改完立即生效，
     * 从来不依赖那个轮询。轮询唯一多兜住的场景是「有人绕过应用直接改库」——
     * 那种情况本就该重启或走接口，不值得为它常驻一个后台线程池。
     */

    /**
     * 初始化系统设置缓存
     */
    @PostConstruct
    private void loadConfigCache() {
        CONFIG_CACHE.clear();
        List<ConfigEntity> entityList = configDao.selectList(null);
        if (SolvelaCollectionUtil.isEmpty(entityList)) {
            return;
        }
        entityList.forEach(entity -> this.CONFIG_CACHE.put(entity.getConfigKey().toLowerCase(), entity));
        log.info("################# 系统配置缓存初始化完毕:{} ###################", CONFIG_CACHE.size());
    }

    /**
     * 刷新系统设置缓存
     */
    private void refreshConfigCache(Long configId) {
        // 重新查询 加入缓存
        ConfigEntity configEntity = configDao.selectById(configId);
        if (null == configEntity) {
            return;
        }
        this.CONFIG_CACHE.put(configEntity.getConfigKey().toLowerCase(), configEntity);
    }

    /**
     * 分页查询系统配置
     *
     */
    public PageResult<ConfigVO> queryConfigPage(ConfigQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<ConfigEntity> entityList = configDao.queryByPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, entityList, ConfigVO.class);
    }

    /**
     * 查询配置缓存
     *
     */
    public ConfigVO getConfig(ConfigKeyEnum configKey) {
        return this.getConfig(configKey.getValue());
    }

    /**
     * 查询配置缓存
     *
     */
    public ConfigVO getConfig(String configKey) {
        if (SolvelaStringUtil.isBlank(configKey)) {
            return null;
        }
        ConfigEntity entity = this.CONFIG_CACHE.get(configKey.toLowerCase());
        return SolvelaBeanUtil.copy(entity, ConfigVO.class);
    }

    /**
     * 查询配置缓存参数
     *
     */
    public String getConfigValue(ConfigKeyEnum configKey) {
        ConfigVO config = this.getConfig(configKey);
        return config == null ? null : config.getConfigValue();
    }

    /**
     * 根据参数key查询 并转换为对象
     *
     */
    public <T> T getConfigValue2Obj(ConfigKeyEnum configKey, Class<T> clazz) {
        String configValue = this.getConfigValue(configKey);
        return JsonUtils.parseObject(configValue, clazz);
    }

    /**
     * 添加系统配置
     *
     */
    public void add(ConfigAddForm configAddForm) {
        ConfigEntity entity = configDao.selectByKey(configAddForm.getConfigKey());
        if (null != entity) {
            throw new BusinessException(UserErrorCode.ALREADY_EXIST);
        }
        entity = SolvelaBeanUtil.copy(configAddForm, ConfigEntity.class);
        configDao.insert(entity);

        // 刷新缓存
        this.refreshConfigCache(entity.getConfigId());
    }

    /**
     * 更新系统配置
     *
     */
    public void updateConfig(ConfigUpdateForm updateDTO) {
        Long configId = updateDTO.getConfigId();
        ConfigEntity entity = configDao.selectById(configId);
        if (null == entity) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }
        ConfigEntity alreadyEntity = configDao.selectByKey(updateDTO.getConfigKey());
        if (null != alreadyEntity && !Objects.equals(configId, alreadyEntity.getConfigId())) {
            throw new BusinessException(UserErrorCode.ALREADY_EXIST, "config key 已存在");
        }

        // 更新数据
        entity = SolvelaBeanUtil.copy(updateDTO, ConfigEntity.class);
        configDao.updateById(entity);

        // 刷新缓存
        this.refreshConfigCache(configId);
    }

    /**
     * 更新系统配置
     *
     */
    public void updateValueByKey(ConfigKeyEnum key, String value) {
        ConfigVO config = this.getConfig(key);
        if (null == config) {
            throw new BusinessException(UserErrorCode.DATA_NOT_EXIST);
        }

        // 更新数据
        Long configId = config.getConfigId();
        ConfigEntity entity = new ConfigEntity();
        entity.setConfigId(configId);
        entity.setConfigValue(value);
        configDao.updateById(entity);

        // 刷新缓存
        this.refreshConfigCache(configId);
    }
}
