package solvela.ledger.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.AssetStrategy;
import solvela.base.common.domain.ResponseDTO;
import solvela.enums.PrizeTypeEnum;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.logistic.domain.entity.PhysicalDelivery;
import solvela.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 实物履约：只负责「生成待发货单」，不负责发货
 *
 * 实物是三段式履约，与积分/现金那种一步到账的资产本质不同：
 *   ① 中奖 -> 本类生成履约单（此刻只知道发什么、发给谁，**不知道寄到哪**）
 *   ② 用户在 C 端补填收货信息
 *   ③ 运营发货、回填物流单号
 * 所以「资产下发成功」对实物而言的含义是「履约单已建立」，而不是「东西已寄出」。
 *
 * @Author weolwo
 */
@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.PHYSICAL)
public class PhysicalAssetHandler implements IAssetHandler {

    /**
     * 履约单初始状态：0-待发货。此时收件三要素为空，运营列表用
     * status=0 AND receiver_address IS NULL 筛出「待用户补地址」的单子
     */
    private static final int STATUS_PENDING_DELIVERY = 0;

    private static final String SOURCE_TYPE_PROPOSAL = "PROPOSAL";

    @Resource
    private PhysicalDeliveryDao deliveryDao;

    @Override
    public ResponseDTO dispatch(ProposalRecord proposal) {
        PhysicalDelivery delivery = new PhysicalDelivery();
        delivery.setMemberId(proposal.getMemberId());
        // 展示快照沿用提案上的那一份（履约单是单据，记的是「中奖当时那个账号」）
        delivery.setMemberName(proposal.getMemberName());
        // 来源单号：本链路存提案 ID。这一列原先叫 proposal_id(bigint)，
        // 泛化成字符串单号之后，商城兑换实物才能以 source_type='MALL' + 订单号 走同一张表。
        delivery.setSourceBizId(String.valueOf(proposal.getId()));
        delivery.setSourceType(SOURCE_TYPE_PROPOSAL);
        delivery.setStatus(STATUS_PENDING_DELIVERY);
        // receiver_name / receiver_phone / receiver_address 刻意不设：
        // 中奖时用户尚未填写地址，v3.37 已把这三列改为可空，由第 ② 步补齐。
        // （此前它们是 NOT NULL 无默认值，实物审批通过必抛
        //   Field 'receiver_name' doesn't have a default value）

        try {
            deliveryDao.insert(delivery);
            log.info(">>>> [实物履约单已建立] 提案ID: {}, 待用户补充收货信息", proposal.getId());
            return ResponseDTO.ok();
        } catch (DuplicateKeyException e) {
            log.warn("【防重拦截】该提案已生成物流单: {}", proposal.getId());
            return ResponseDTO.ok();
        }
    }
}
