package solvela.admin.module.system.dict.manager;

import jakarta.annotation.Resource;
import solvela.base.util.SolvelaBeanUtil;
import solvela.admin.constant.AdminCacheConst;
import solvela.admin.module.system.dict.dao.DictDao;
import solvela.admin.module.system.dict.dao.DictDataDao;
import solvela.admin.module.system.dict.domain.entity.DictDataEntity;
import solvela.admin.module.system.dict.domain.entity.DictEntity;
import solvela.admin.module.system.dict.domain.vo.DictDataVO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * 数据字典 缓存
 *
 * @Author 1024创新实验室-主任-卓大
 * @Date 2025-03-25 22:25:04
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Service
public class DictManager {

    @Resource
    private DictDao dictDao;

    @Resource
    private DictDataDao dictDataDao;


    /**
     * 获取字典
     */
    @Cacheable(value = AdminCacheConst.Dict.DICT_DATA, key = "#dictCode + '_' + #dataValue")
    public DictDataVO getDictData(String dictCode, String dataValue) {
        DictEntity dictEntity = dictDao.selectByCode(dictCode);
        if (dictEntity == null) {
            return null;
        }

        DictDataEntity dictDataEntity = dictDataDao.selectByDictIdAndValue(dictEntity.getDictId(), dataValue);
        return SolvelaBeanUtil.copy(dictDataEntity, DictDataVO.class);
    }

    /**
     * 按标签反查字典项（Excel 导入要把"广东"还原成码值）。
     *
     * <p><b>为什么返回 List 而不是单个</b>：
     * <ul>
     *   <li>同一字典下标签没有唯一约束，理论上会重复。撞了必须让调用方知道，不能随便挑一个回去 ——
     *       映射错了就是往库里写脏数据。</li>
     *   <li>缓存配置是 {@code disableCachingNullValues}，返回 null 会在写缓存时抛异常。
     *       而"标签查不到"在导入场景里是<b>常态</b>（用户填错字），
     *       返回空 List 既能正常缓存、又不会把这条路变成异常路径。</li>
     * </ul>
     */
    @Cacheable(value = AdminCacheConst.Dict.DICT_DATA_LABEL, key = "#dictCode + '_' + #dataLabel")
    public List<DictDataVO> listDictDataByLabel(String dictCode, String dataLabel) {
        DictEntity dictEntity = dictDao.selectByCode(dictCode);
        if (dictEntity == null) {
            return List.of();
        }

        List<DictDataEntity> matched = dictDataDao.selectByDictIdAndLabel(dictEntity.getDictId(), dataLabel);
        return SolvelaBeanUtil.copyList(matched, DictDataVO.class);
    }

}
