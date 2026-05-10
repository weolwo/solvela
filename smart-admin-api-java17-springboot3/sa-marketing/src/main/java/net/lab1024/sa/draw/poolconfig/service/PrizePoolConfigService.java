package net.lab1024.sa.draw.poolconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.poolconfig.dao.PrizePoolConfigDao;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 奖池配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizePoolConfigService {

    private final PrizePoolConfigDao prizePoolConfigDao;

    /**
     * 分页查询
     */
    public PageResult<PrizePoolConfigVO> queryPage(PrizePoolConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizePoolConfigVO> list = prizePoolConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PrizePoolConfigAddForm addForm) {
        PrizePoolConfig prizePoolConfig = SmartBeanUtil.copy(addForm, PrizePoolConfig.class);
        prizePoolConfigDao.insert(prizePoolConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PrizePoolConfigUpdateForm updateForm) {
        PrizePoolConfig prizePoolConfig = SmartBeanUtil.copy(updateForm, PrizePoolConfig.class);
        prizePoolConfigDao.updateById(prizePoolConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        prizePoolConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        prizePoolConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
