package sa.lottery.prizerule.service;

import java.util.List;
import sa.lottery.prizerule.dao.LotteryPrizeRuleDao;
import sa.lottery.prizerule.domain.form.LotteryPrizeRuleQueryForm;
import sa.lottery.prizerule.domain.vo.LotteryPrizeRuleVO;
import sa.base.common.util.SmartPageUtil;
import sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 彩票奖励配置 Service —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / batchDelete / delete 已刻意移除</h3>
 * 生成器产出的那四个方法只是 {@code SmartBeanUtil.copy} + DAO 调用，<b>一条校验都没有</b>，
 * 而奖级规则恰恰是全系统最不能随便写的数据之一：
 * <ul>
 *   <li>匹配规则写成非法值 → 开奖时 {@code LotterySettleService} 记一条 error 后<b>整条跳过</b>，
 *       该奖级一张奖也发不出去，而日志没人天天看；</li>
 *   <li>奖品编码写错 → 派奖时报「奖品配置不存在」，用户中了奖拿不到东西；</li>
 *   <li>匹配长度超过号码长度 → {@code TicketMatcher} 判不中，这条规则永远是死的。</li>
 * </ul>
 * 这些工作台的 {@code validateRules} 全都会拦，而这里全都放行 —— 等于给校验墙开了个后门。
 *
 * <p>更要命的是即便补上校验也没意义：工作台按玩法<b>整表重建</b>奖级
 * （先删后插），从这里写进去的规则活不过下一次工作台保存。
 * 所以正确的做法不是给后门加锁，而是把门拆掉：奖级规则只有工作台一个写入口。
 *
 * <p>本 Service 现在只服务于「查」——原始行分页留给排查与导出，
 * 赔付模型与体检在 {@link LotteryPrizeAnalysisService}。
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryPrizeRuleService {

    private final LotteryPrizeRuleDao lotteryPrizeRuleDao;

    /**
     * 分页查询：奖级规则原始行。
     *
     * 页面主视图走 {@link LotteryPrizeAnalysisService#analysis} 的按玩法分组结果，
     * 这个接口保留下来是给排查用的 —— 需要看某条规则的原始字段（含创建人、创建时间）时。
     */
    public PageResult<LotteryPrizeRuleVO> queryPage(LotteryPrizeRuleQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryPrizeRuleVO> list = lotteryPrizeRuleDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }
}
