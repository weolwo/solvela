package solvela.member.api;

import solvela.enums.ProposalStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条优惠记录。
 *
 * <h3>🔴 状态带枚举出来，翻译在网关</h3>
 * {@code ProposalStatusEnum} 有十种取值，其中「待一审」「待二审」「风控拦截」
 * 是<b>运营视角</b>的说法。契约照原样带出来，由网关翻成给用户看的话 ——
 * 域不该猜下游要怎么展示，而网关本来就在做这件事（见 RecordService）。
 *
 * <p>带枚举而不是 int，是为了让网关那边的 switch <b>不写 default</b> 也能穷尽 ——
 * 将来加一个状态时那里编译不过，而不是悄悄显示成「处理中」。
 *
 * @param assetName 资产展示名（券名 / 商品名）。可能为空 —— 值类资产（积分、现金）
 *                  本来就没有名字，那时该由展示层用资产类型兜底
 * @param amount    发放数量。积分是整数、现金是两位小数，但库里是
 *                  {@code decimal(13,4)} 一种类型，所以这里统一用 BigDecimal，
 *                  <b>怎么展示由端上按 assetType 决定</b>
 * @param remark    失败/拦截原因，或调用方传入的场景说明。
 *                  🔴 <b>这句话不能直接给用户看</b>：风控拦截时它写的是拦截原因，
 *                  告诉用户「单笔超限」等于告诉他下次怎么绕
 */
public record ProposalRecordView(
        Long recordId,
        String assetType,
        String assetRef,
        String assetName,
        BigDecimal amount,
        ProposalStatusEnum status,
        String remark,
        LocalDateTime createTime) {
}
