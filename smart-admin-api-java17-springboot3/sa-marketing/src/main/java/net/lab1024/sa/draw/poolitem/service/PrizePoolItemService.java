package net.lab1024.sa.draw.poolitem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.poolitem.dao.PrizePoolItemDao;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
import net.lab1024.sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 奖池奖项库 Service —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / batchDelete / delete 已刻意移除</h3>
 * 生成器产出的那四个方法只是 {@code SmartBeanUtil.copy} + DAO 调用，一条校验都没有，
 * 而这张表存的是<b>库存</b>，是全模块最不能随手写的数据：
 * <ul>
 *   <li>{@code PrizePoolItemAddForm} / {@code UpdateForm} 都开放了 <b>{@code usedStock}</b> ——
 *       跨奖池累计已出数量、库存对账的基准。工作台落库处明写「used_stock/version 永不接受前端值」，
 *       这条路径却照单全收。手改一个数，DB 账目当场错乱，
 *       而运行态真正用来预扣的 Redis 剩余量根本不会跟着变，两个口径就此漂移；</li>
 *   <li>{@code delete} 没有守卫：删掉仍被坑位映射引用的奖项，
 *       抽奖直接返回「奖池配置异常：奖项已被删除」；</li>
 *   <li>活动已上线时工作台限制「库存只允许追加，不允许缩减」，这条路径完全不看活动状态。</li>
 * </ul>
 *
 * <p>本 Service 现在只服务于「查」——原始行分页留给排查与导出，
 * 库存看板在 {@link PrizeItemStockService}。
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizePoolItemService {

    private final PrizePoolItemDao prizePoolItemDao;

    /**
     * 分页查询：奖项原始行。
     *
     * 页面主视图走 {@link PrizeItemStockService#stockBoard}，
     * 这个接口留给需要看某个奖项原始字段（含 version、创建时间）的排查场景。
     */
    public PageResult<PrizePoolItemVO> queryPage(PrizePoolItemQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizePoolItemVO> list = prizePoolItemDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }
}
