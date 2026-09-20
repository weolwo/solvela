package solvela.ledger.logistic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.member.api.DeliveryApi;
import solvela.enums.DeliveryStatusEnum;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.member.api.DeliveryFillResult;
import solvela.member.api.DeliveryReceiverCmd;
import solvela.member.api.MemberDeliveryView;

import java.util.List;

/**
 * 实物履约单的<b>会员侧</b>：我的东西呢，以及寄到哪。
 *
 * <h3>它补的是三段式履约里一直缺的第 ②</h3>
 * {@code PhysicalAssetHandler} 的类注释把流程写成：
 * ① 中奖生成履约单（不知道寄到哪）→ ② <b>用户在 C 端补填收货信息</b> → ③ 运营发货回填单号。
 * 第 ② 步从来没有入口，只能靠客服手工改库 —— 本类就是那一步。
 *
 * <h3>🔴 和管理端的 {@code PhysicalDeliveryService} 是两个类，刻意的</h3>
 * 那个是运营视角：分页、导入、批量作废、看所有人的单。
 * 本类是会员视角：<b>每一个方法都带 memberId 且它进查询条件</b>。
 * 合成一个类的话，迟早有人在会员侧复用一个「运营版」的方法 ——
 * 那个方法不带 memberId，表现是<b>能查到别人的收货地址</b>，而且不报错。
 *
 * <h3>⚠️ {@code @Primary} 不能删</h3>
 * app-biz 进程里有两个 {@link DeliveryApi} 实现：本类（真实现）和
 * {@code DeliveryInternalController}（给网关跨进程用的 HTTP 薄壳）。
 * 少了 {@code @Primary}，同进程内按接口注入的商城会拿到<b>薄壳</b> ——
 * 那个薄壳又注入本类，绕一圈还能跑，但多一层毫无意义的转发；
 * 更糟的是薄壳哪天改成按接口注入，就变成一次无限递归的自调用。
 * 与 {@code CouponQueryApiService} 的处理完全一致。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class MemberDeliveryService implements DeliveryApi {

    /** 一次最多给多少条。实物单量天然很小，给多了只是白传 */
    private static final int MAX_LIMIT = 50;

    /** 存量单没有奖品名（加列之前建的），给一个能读的兜底而不是空白 */
    private static final String UNKNOWN_PRIZE = "实物奖品";

    private final PhysicalDeliveryDao physicalDeliveryDao;

    /**
     * 我的实物履约单，新的在前。
     *
     * <p>中奖的和商城兑的<b>混在一起</b>：用户不关心「这件东西是抽中的还是兑的」，
     * 他只想知道「我的东西呢」。要区分看 {@code sourceType}。
     */
    @Override
    public List<MemberDeliveryView> listMine(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int size = limit <= 0 ? MAX_LIMIT : Math.min(limit, MAX_LIMIT);
        List<PhysicalDelivery> list = physicalDeliveryDao.selectList(
                new LambdaQueryWrapper<PhysicalDelivery>()
                        // 🔴 memberId 必须进条件。少了它这就是「查所有人的收货地址」
                        .eq(PhysicalDelivery::getMemberId, memberId)
                        .orderByDesc(PhysicalDelivery::getId)
                        .last("limit " + size));
        return list.stream().map(MemberDeliveryService::toView).toList();
    }

    /**
     * 补填收件信息。
     *
     * <h3>🔴 三道校验，每一道都是「不做会怎样」</h3>
     * <ol>
     *   <li><b>单子是我的</b> —— {@code memberId} 进 UPDATE 的 WHERE。
     *       少了它就是「可以改别人的收货地址」，而且改成功了也不报错；</li>
     *   <li><b>还能填</b> —— 只有待发货能填。已发货之后改地址，货还是寄到老地址，
     *       但用户看到「保存成功」，会一直等一个永远不会到的包裹；</li>
     *   <li><b>条件更新</b> —— 状态判断和写入是<b>同一条 SQL</b>，不是先查后写。
     *       先查后写的话，用户点提交和运营点发货撞在一起时，两边都会成功。</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryFillResult fillReceiver(DeliveryReceiverCmd cmd) {
        if (cmd.deliveryId() == null || cmd.memberId() == null) {
            return DeliveryFillResult.reject("参数不完整");
        }
        if (StringUtils.isBlank(cmd.receiverName())
                || StringUtils.isBlank(cmd.receiverPhone())
                || StringUtils.isBlank(cmd.receiverAddress())) {
            return DeliveryFillResult.reject("收件人、电话、地址都要填");
        }

        PhysicalDelivery update = new PhysicalDelivery();
        update.setReceiverName(cmd.receiverName());
        update.setReceiverPhone(cmd.receiverPhone());
        update.setReceiverAddress(cmd.receiverAddress());

        /*
         * 条件更新：id + memberId + status=待发货 三个一起进 WHERE。
         *
         * ⚠️ 用 update(entity, wrapper) 而不是 updateById：后者不带 memberId，
         *    等于「谁都能改任何一张单」。这条链路今天只有网关能调，
         *    但「今天只有谁能调」不是安全边界 —— WHERE 才是。
         *
         * 🔴 走实体 + wrapper 这条路，加密才会生效：三列挂着 PiiTypeHandler，
         *    而 MyBatis-Plus 只对实体字段用 typeHandler。
         *    改成 wrapper.set(字段, 明文) 的话会以【明文入库】，且不报任何错。
         */
        int changed = physicalDeliveryDao.update(update, new LambdaQueryWrapper<PhysicalDelivery>()
                .eq(PhysicalDelivery::getId, cmd.deliveryId())
                .eq(PhysicalDelivery::getMemberId, cmd.memberId())
                .eq(PhysicalDelivery::getStatus, DeliveryStatusEnum.PENDING));

        if (changed == 0) {
            /*
             * 抢不到。三种可能，对用户是同一句话：
             *   · 单子不存在，或不是他的（不区分 —— 区分了就能拿这个接口探测别人的单号）；
             *   · 已经发货 / 签收 / 取消了。
             */
            PhysicalDelivery current = physicalDeliveryDao.selectOne(
                    new LambdaQueryWrapper<PhysicalDelivery>()
                            .eq(PhysicalDelivery::getId, cmd.deliveryId())
                            .eq(PhysicalDelivery::getMemberId, cmd.memberId()));
            log.info("【实物补填】{} 没填上，当前状态 {}", cmd.deliveryId(),
                    current == null ? "查不到或不是本人的" : current.getStatus());
            return DeliveryFillResult.reject(current == null
                    ? "这件奖品不存在"
                    : "这件奖品已经在发货流程里了，改不了收货信息。需要修改请联系客服");
        }

        log.info("【实物补填】{} 收货信息已保存, memberId: {}", cmd.deliveryId(), cmd.memberId());
        return DeliveryFillResult.ok();
    }

    /**
     * 实体 → 会员视角。
     *
     * <h3>🔴 这是唯一的下发出口，脱敏只在这里做一次</h3>
     * 多一处构造 {@code MemberDeliveryView} 就多一条绕过脱敏的路径，
     * 而那条路径不会有任何报错。{@code DeliveryPrivacyTest} 盯着这件事 ——
     * 形状对齐商城地址簿那边的 {@code AddressPrivacyTest}。
     */
    private static MemberDeliveryView toView(PhysicalDelivery entity) {
        DeliveryStatusEnum status = entity.getStatus();
        return new MemberDeliveryView(
                entity.getId(),
                StringUtils.defaultIfBlank(entity.getPrizeName(), UNKNOWN_PRIZE),
                entity.getSourceType(),
                entity.getSourceBizId(),
                status == null ? null : status.getValue(),
                statusText(status, entity),
                needAddress(status, entity),
                entity.getReceiverName(),
                // 🔴 脱敏。口径与 MallClientFacade.maskPhone 一致，实现见 maskPhone
                maskPhone(entity.getReceiverPhone()),
                entity.getReceiverAddress(),
                entity.getLogisticsCompany(),
                entity.getLogisticsNo(),
                entity.getCreateTime());
    }

    /**
     * 还差收件信息吗 —— 「去填地址」按钮<b>唯一</b>的判据。
     *
     * <p>做成服务端给的字段而不是让端上按 status 推：什么时候还能填是状态机的一部分，
     * 各端各推一份的话，状态机改一次就会有一个端开始给出错的按钮。
     * 这条和 {@code MallOrderView.payable} 是同一条规矩。
     */
    private static boolean needAddress(DeliveryStatusEnum status, PhysicalDelivery entity) {
        return status == DeliveryStatusEnum.PENDING && StringUtils.isBlank(entity.getReceiverAddress());
    }

    /**
     * 给用户看的一句话。
     *
     * <p>用 switch 表达式且<b>不给兜底分支</b>：资产域新增一个状态时这里编译不过，
     * 而不是悄悄落进 default 显示成「处理中」。与网关翻译任务状态是同一个理由。
     */
    private static String statusText(DeliveryStatusEnum status, PhysicalDelivery entity) {
        if (status == null) {
            return "处理中";
        }
        return switch (status) {
            // 待发货分两种，对用户是完全不同的两件事：一种要他动手，一种要他等
            case PENDING -> StringUtils.isBlank(entity.getReceiverAddress()) ? "待填写收货信息" : "待发货";
            case DELIVERED -> "已发货";
            case SIGNED -> "已签收";
            // 「异常退回」是运营视角的词。对用户说清楚下一步该干什么
            case RETURNED -> "投递失败，请联系客服";
            case CANCELLED -> "已取消";
        };
    }

    /**
     * 手机号脱敏：留头 3 尾 4。
     *
     * <h3>⚠️ 这是本仓第二份实现，和 {@code MallClientFacade.maskPhone} 重复</h3>
     * 抽不掉：那一份在 {@code solvela-mall}，本类在 {@code solvela-ledger}，
     * 而 {@code MallLedgerBoundaryTest} 守着两者之间那条缝 —— 为一个 6 行的工具函数
     * 把商城拖进资产域的 classpath，代价比重复大得多。
     *
     * <p>真要合并，该往<b>下</b>放（{@code solvela-base-core} 的 util），
     * 不是让两个域互相认识。那件事等到第三个域也要脱敏时再做 ——
     * 两份的时候合并是投机，三份就是该还的债了。
     *
     * <p>🔴 短号不能反而暴露更多：{@code "1"} 不该脱敏成 {@code "1****"}。
     * 非大陆号码（库里已经有菲律宾的地址）同样要遮住中间段。
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            // 空值原样返回，不要拼出一串星号让前端以为有号码
            return phone;
        }
        if (phone.length() <= 4) {
            return "*".repeat(phone.length());
        }
        int head = Math.min(3, (phone.length() - 1) / 2);
        int tail = Math.min(4, phone.length() - head - 1);
        return phone.substring(0, head) + "****" + phone.substring(phone.length() - tail);
    }
}
