package solvela.prize.prizeconfig.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖品名录：把流水里的<b>奖品编码</b>翻译成人看的名字、类型与价值。
 *
 * <p>抽奖流水、购彩记录、奖池分析、库存看板都只存 {@code prize_code} —— 存编码而不是存名字
 * 是对的（名字会改），代价是每个统计页面都要自己做一次翻译。改造前这段
 * {@code lambdaQuery().in(...).stream().collect(toMap(...))} 在五个 Service 里各写了一遍。
 *
 * <h3>翻译不出来是正常情况</h3>
 * 奖品配置可以被删，流水不会跟着删（也不该跟着删，它是发奖凭证）。
 * 所以查不到就是查不到，调用方<b>显示编码本身</b>即可，不要因此报错或过滤掉这一行 ——
 * 那会让「发过一个现在已下线的奖」这件事从报表上凭空消失。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
@RequiredArgsConstructor
@Service
public class PrizeCatalog {

    private final PrizeConfigManager prizeConfigManager;

    /**
     * 按编码批量翻译。空集合直接返回空表，不查库 ——
     * {@code in ()} 是个语法错误，MyBatis-Plus 会把它拼成恒假条件，白跑一次往返。
     *
     * <p>同一个编码在不同活动下可能各有一份配置，撞了取先查到的那份：
     * 这里只用来取名字与类型，两份配置的这几列本就该一样。
     */
    public Map<String, PrizeConfig> mapByCodes(Collection<String> codes) {
        // 分组键可能为空（流水里存在没写奖品编码的行），null 混进 in 会变成一个永远不匹配的条件
        List<String> wanted = codes == null ? List.of()
                : codes.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return prizeConfigManager.lambdaQuery().in(PrizeConfig::getPrizeCode, wanted).list().stream()
                .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (first, ignored) -> first));
    }

    /**
     * 整表翻译。
     *
     * <p>⚠️ 只给「本来就要遍历全部奖品」的配置体检页用（奖池分析、库存看板）——
     * 那些页面要回答的是「配了却没挂上的奖有哪些」，缺的那部分恰恰不在流水里，
     * 按编码查反而查不全。流水类页面一律用 {@link #mapByCodes}。
     */
    public Map<String, PrizeConfig> mapAll() {
        return prizeConfigManager.lambdaQuery().list().stream()
                .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (first, ignored) -> first));
    }

    /**
     * 已发出价值 = 单价 × 件数，两位小数。
     *
     * <p>没配单价时返回 null 而不是 0：0 的意思是「这个奖不值钱」，null 的意思是
     * 「不知道值多少」，报表上前者会被算进合计，后者应当显示成空白。
     */
    public static BigDecimal issuedValue(PrizeConfig prize, long count) {
        if (prize == null || prize.getPrizeValue() == null) {
            return null;
        }
        return prize.getPrizeValue().multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.HALF_UP);
    }
}
