package solvela.marketing.api;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 我的奖励记录。实现在 {@code solvela-prize}。
 *
 * <h3>为什么单独一个接口，不塞进 ActivityApi</h3>
 * 本模块的 pom 写着「里面按<b>实现归属</b>分成多个接口，不是一个大接口 ——
 * 合成一个的话没法装配：一个 Spring bean 实现不了半个接口，而这两半在不同的 maven 模块里」。
 * {@code ActivityApi} 由 solvela-marketing 的 {@code ActivityFacade} 实现，
 * 而奖励记录的表与服务都在 solvela-prize。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 服务于服务间调用。公网入口是网关自己的 {@code /records}，鉴权与字段裁剪在那一层 ——
 * 尤其是 {@code failReason}，那是内部原因，不该原样给用户看。
 */
@HttpExchange("/internal/prize")
public interface PrizeRecordApi {

    /**
     * 某个会员<b>最近</b>的奖励记录，按时间倒序。
     *
     * <p>⚠️ <b>没有分页，只有 limit</b>。C 端目前只有「我的」页那个「最近几条」的用法，
     * 而 t_prize_log 是会随抽奖次数线性增长的表 —— 一次全捞是不行的。
     * 真做「全部奖励记录」那一页时再加分页，<b>形状由那时的需求定</b>
     *（cursor 还是 offset 取决于那一页要不要跳页）。现在猜一个壳子，
     * 猜错了比没有更难改。
     *
     * <p>没有记录时返回<b>空列表，不是 null</b> —— 新用户就是这个状态。
     */
    /**
     * 我的奖励记录，按时间倒序。
     *
     * @param activityCode 只看这一个活动的记录，<b>为空表示不限</b>。
     *                     活动专题页要的是「我在这个活动里得过什么」——
     *                     而过滤必须在服务端做：客户端拿最近 20 条再筛，
     *                     在参与多个活动的用户身上会筛出空列表，
     *                     而他明明中过奖，只是那条排在第 21 位
     */
    @GetExchange("/record")
    List<PrizeRecordView> listRecentRecords(@RequestParam Long memberId,
                                            @RequestParam(required = false) String activityCode,
                                            @RequestParam int limit);
}
