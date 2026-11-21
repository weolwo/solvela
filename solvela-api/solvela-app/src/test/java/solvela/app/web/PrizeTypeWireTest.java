package solvela.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.app.domain.AssetView;
import solvela.enums.PrizeTypeEnum;
import solvela.member.api.MemberAssetView;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 资产类型在线上的形状：2026-11-21 契约里的 assetType 从 String 改成 PrizeTypeEnum，JSON 必须仍是枚举名。
 *
 * <p>网关与 app-biz 分开发版，发版间隙里一边是旧版：旧版写的是字符串 "SCORE"，新版读得进；
 * 新版写出的也必须还是 "SCORE"，旧版和 C 端前端才认得。
 */
class PrizeTypeWireTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("旧版 app-biz 写的字符串，新版网关读成枚举")
    void 旧格式读得进() {
        MemberAssetView view = mapper.readValue(
                "{\"assetType\":\"SCORE\",\"balance\":12.5,\"frozen\":false}", MemberAssetView.class);

        assertThat(view.assetType()).isEqualTo(PrizeTypeEnum.SCORE);
    }

    @Test
    @DisplayName("写给 C 端的仍是枚举名，前端不用改")
    void 写出仍是枚举名() {
        String json = mapper.writeValueAsString(new AssetView(PrizeTypeEnum.BALANCE, "余额", "10.5", true, false));

        assertThat(json).contains("\"assetType\":\"BALANCE\"");
    }

    @Test
    @DisplayName("⚠️ 枚举外的值整条反序列化失败 —— 以后给 PrizeTypeEnum 加值，要先发读的一方（网关）")
    void 未知值读不进() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"assetType\":\"DIAMOND\",\"balance\":1,\"frozen\":false}", MemberAssetView.class))
                .isInstanceOf(InvalidFormatException.class);
    }
}
