package sa.draw.prizemapping.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.PageResult;
import sa.base.common.util.SmartPageUtil;
import sa.draw.prizemapping.dao.PoolPrizeMappingDao;
import sa.draw.prizemapping.domain.form.PoolPrizeMappingQueryForm;
import sa.draw.prizemapping.domain.vo.PoolPrizeMappingVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 奖池奖项映射 Service —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / batchDelete / delete 已刻意移除</h3>
 * 生成器产出的那四个方法只是 {@code SmartBeanUtil.copy} + DAO 调用，<b>一条校验都没有</b>，
 * 而坑位映射是全模块最不能随便写的数据：
 * <ul>
 *   <li><b>概率必须整池闭环到 100%。</b>差一点点，{@code DrawPoolSnapshot} 构造就抛
 *       {@code IllegalArgumentException}，而抽奖执行链路没有捕获它 ——
 *       后果不是某个奖抽不到，而是<b>这个奖池的每一次抽奖请求都直接报错</b>；</li>
 *   <li><b>每池最多一个兜底。</b>多配的会被引擎静默忽略；</li>
 *   <li><b>坑位引用的奖项必须存在</b>，否则抽奖返回「奖池配置异常：奖项已被删除」；</li>
 *   <li>活动已上线时<b>禁止增删坑位</b>（概率可调）。</li>
 * </ul>
 * 这些工作台的 {@code workbenchSave} 全都会拦，而这里全都放行。
 *
 * <p>更关键的是补校验也没意义：工作台按池<b>整表重建</b>坑位（先删后插），
 * 从这里写进去的映射活不过下一次工作台保存。所以正确做法不是给后门加锁，而是把门拆掉。
 *
 * <p>本 Service 现在只服务于「查」——原始行分页留给排查与导出，
 * 概率结构分析与体检在 {@link DrawPoolAnalysisService}。
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PoolPrizeMappingService {

    private final PoolPrizeMappingDao poolPrizeMappingDao;

    /**
     * 分页查询：坑位映射原始行。
     *
     * 页面主视图走 {@link DrawPoolAnalysisService#analysis} 的按池分组结果，
     * 这个接口留给需要看某条映射原始字段（含创建人、创建时间）的排查场景。
     */
    public PageResult<PoolPrizeMappingVO> queryPage(PoolPrizeMappingQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PoolPrizeMappingVO> list = poolPrizeMappingDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }
}
