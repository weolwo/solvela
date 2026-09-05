package solvela.member.api;

/**
 * 发放结果。
 *
 * @param fulfillRefId 履约单引用：发货单 id / 券 id / 流水 id。受理时必有值，
 *                     落到商城订单的 {@code fulfill_ref_id}，是「东西发到哪去了」的唯一线索。
 *                     多份券时给第一张的 id —— 它们的 source_biz_id 同前缀，够顺藤摸瓜
 */
public record AssetGrantResult(boolean accepted, AssetGrantReason reason, String fulfillRefId) {

    public static AssetGrantResult ofAccepted(String fulfillRefId) {
        return new AssetGrantResult(true, null, fulfillRefId);
    }

    public static AssetGrantResult ofReject(AssetGrantReason reason) {
        return new AssetGrantResult(false, reason, null);
    }
}
