package solvela.admin.module.system.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import solvela.base.domain.PageResult;
import solvela.web.ResponseDTO;
import solvela.exception.BusinessException;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.admin.constant.AdminCacheConst;
import solvela.admin.module.system.dict.dao.DictDao;
import solvela.admin.module.system.dict.dao.DictDataDao;
import solvela.admin.module.system.dict.domain.entity.DictDataEntity;
import solvela.admin.module.system.dict.domain.entity.DictEntity;
import solvela.admin.module.system.dict.domain.form.*;
import solvela.admin.module.system.dict.domain.vo.DictDataVO;
import solvela.admin.module.system.dict.domain.vo.DictVO;
import solvela.admin.module.system.dict.manager.DictManager;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据字典 Service
 *
 * @Author 1024创新实验室-主任-卓大
 * @Date 2025-03-25 22:25:04
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Service
public class DictService {

    @Resource
    private DictDao dictDao;

    @Resource
    private DictDataDao dictDataDao;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private DictManager dictManager;

    /**
     * 获取全部数据
     */
    public List<DictDataVO> getAll() {
        return dictDataDao.getAll();
    }

    /**
     * 获取所有字典
     */
    public List<DictVO> getAllDict() {
        List<DictEntity> dictEntityList = dictDao.selectList(null).stream().filter(e -> !e.getDisabledFlag()).collect(Collectors.toList());
        return SolvelaBeanUtil.copyList(dictEntityList, DictVO.class);
    }

    /**
     * 分页查询
     */
    public PageResult<DictVO> queryPage(DictQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<DictVO> list = dictDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public synchronized ResponseDTO<String> add(DictAddForm addForm) {
        DictEntity existDictCode = dictDao.selectByCode(addForm.getDictCode());
        if (null != existDictCode) {
            return ResponseDTO.userErrorParam("数据字典编码已经存在！");
        }

        DictEntity dictEntity = SolvelaBeanUtil.copy(addForm, DictEntity.class);
        dictDao.insert(dictEntity);
        return ResponseDTO.ok();
    }

    /**
     * 禁用  启用
     */
    public ResponseDTO<String> updateDisabled(Long dictId) {
        DictEntity dictEntity = dictDao.selectById(dictId);
        if (dictEntity == null) {
            return ResponseDTO.userErrorParam("数据不存在");
        }

        dictEntity.setDisabledFlag(!dictEntity.getDisabledFlag());
        dictDao.updateById(dictEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    @Caching(evict = {
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA, allEntries = true),
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA_LABEL, allEntries = true)
    })
    public synchronized ResponseDTO<String> update(DictUpdateForm updateForm) {
        DictEntity existDictCode = dictDao.selectByCode(updateForm.getDictCode());
        if (null != existDictCode && !existDictCode.getDictId().equals(updateForm.getDictId())) {
            return ResponseDTO.userErrorParam("数据字典编码已经存在！");
        }

        DictEntity dictEntity = SolvelaBeanUtil.copy(updateForm, DictEntity.class);
        dictDao.updateById(dictEntity);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    @Caching(evict = {
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA, allEntries = true),
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA_LABEL, allEntries = true)
    })
    public synchronized ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        dictDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    @Caching(evict = {
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA, allEntries = true),
            @CacheEvict(value = AdminCacheConst.Dict.DICT_DATA_LABEL, allEntries = true)
    })
    public synchronized ResponseDTO<String> delete(Long dictId) {
        if (null == dictId) {
            return ResponseDTO.ok();
        }

        dictDao.deleteById(dictId);
        return ResponseDTO.ok();
    }


    // -------------- 字典数据 --------------------

    /**
     * 分页查询
     */
    public List<DictDataVO> queryDictData(Long dictId) {
        return dictDataDao.queryByDictId(dictId);
    }

    /**
     * 获取字典
     */

    public DictDataVO getDictData(String dictCode, String dataValue) {
        return dictManager.getDictData(dictCode, dataValue);
    }

    /**
     * 获取字典Label
     */
    public String getDictDataLabel(String dictCode, String dataValue) {
        DictDataVO dictData = getDictData(dictCode, dataValue);
        return dictData == null ? "" : dictData.getDataLabel();
    }

    /**
     * 按标签反查码值（Excel 导入用：用户填的是"广东"，库里要存 "GD"）。
     *
     * @return 查不到返回 null
     * @throws BusinessException 同一字典下有多个同名标签时。映射有歧义还硬挑一个，
     *                           就是往库里写脏数据；这属于字典配置错误，必须让人看见
     */
    public String getDictDataValueByLabel(String dictCode, String dataLabel) {
        List<DictDataVO> matched = dictManager.listDictDataByLabel(dictCode, dataLabel);
        if (SolvelaCollectionUtil.isEmpty(matched)) {
            return null;
        }
        if (matched.size() > 1) {
            throw new BusinessException("字典 " + dictCode + " 下存在 " + matched.size()
                    + " 个同名标签「" + dataLabel + "」，无法确定对应的码值，请先修正字典配置");
        }
        return matched.getFirst().getDataValue();
    }

    /**
     * 添加
     */
    public synchronized ResponseDTO<String> addDictData(DictDataAddForm addForm) {

        addForm.setDataValue(SolvelaStringUtil.trim(addForm.getDataValue()));

        DictEntity dictEntity = dictDao.selectById(addForm.getDictId());
        if (null == dictEntity) {
            return ResponseDTO.userErrorParam("数据字典不存在");
        }

        DictDataEntity existData = dictDataDao.selectByDictIdAndValue(addForm.getDictId(), addForm.getDataValue());
        if (null != existData) {
            return ResponseDTO.userErrorParam("已存在相同value的数据");
        }

        DictDataEntity dictDataEntity = SolvelaBeanUtil.copy(addForm, DictDataEntity.class);
        dictDataDao.insert(dictDataEntity);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    public synchronized ResponseDTO<String> updateDictData(DictDataUpdateForm updateForm) {

        updateForm.setDataValue(SolvelaStringUtil.trim(updateForm.getDataValue()));

        // ⚠️ 必须在更新前清：清缓存要按数据库里的旧 code/value/label 去 evict，
        // 改完再清就只能拿到新值，旧 key 会永远留在缓存里
        clearDictDataCache(Collections.singletonList(updateForm.getDictDataId()));

        DictEntity dictEntity = dictDao.selectById(updateForm.getDictId());
        if (null == dictEntity) {
            return ResponseDTO.userErrorParam("数据字典不存在");
        }

        DictDataEntity existData = dictDataDao.selectByDictIdAndValue(updateForm.getDictId(), updateForm.getDataValue());
        if (null != existData && !existData.getDictDataId().equals(updateForm.getDictDataId())) {
            return ResponseDTO.userErrorParam("已存在相同value的数据");
        }

        DictDataEntity dictDataEntity = SolvelaBeanUtil.copy(updateForm, DictDataEntity.class);
        dictDataDao.updateById(dictDataEntity);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public synchronized ResponseDTO<String> batchDeleteDictData(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }
        // 清除缓存
        clearDictDataCache(idList);
        // 删除
        dictDataDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public synchronized ResponseDTO<String> deleteDictData(Long dictDataId) {
        if (null == dictDataId) {
            return ResponseDTO.ok();
        }
        // 清除缓存
        clearDictDataCache(Collections.singletonList(dictDataId));
        // 删除
        dictDataDao.deleteById(dictDataId);
        return ResponseDTO.ok();
    }


    /**
     * 清空字典数据缓存
     */
    private void clearDictDataCache(List<Long> idList) {
        List<DictDataVO> dictDataList = dictDataDao.selectByDictDataIds(idList);
        Cache valueCache = cacheManager.getCache(AdminCacheConst.Dict.DICT_DATA);
        Cache labelCache = cacheManager.getCache(AdminCacheConst.Dict.DICT_DATA_LABEL);

        for (DictDataVO dictDataVO : dictDataList) {
            if (valueCache != null) {
                valueCache.evict(dictDataVO.getDictCode() + "_" + dictDataVO.getDataValue());
            }
            // 正查和反查是两个缓存，漏掉任何一个都会让「字典改了、导入还按旧配置映射」
            if (labelCache != null) {
                labelCache.evict(dictDataVO.getDictCode() + "_" + dictDataVO.getDataLabel());
            }
        }
    }


    /**
     * 更新启用/禁用
     */
    public synchronized ResponseDTO<String> updateDictDataDisabled(Long dictDataId) {
        DictDataEntity dictDataEntity = dictDataDao.selectById(dictDataId);
        if (dictDataEntity == null) {
            return ResponseDTO.userErrorParam("数据不存在");
        }

        dictDataEntity.setDisabledFlag(!dictDataEntity.getDisabledFlag());
        dictDataDao.updateById(dictDataEntity);
        return ResponseDTO.ok();
    }

}
