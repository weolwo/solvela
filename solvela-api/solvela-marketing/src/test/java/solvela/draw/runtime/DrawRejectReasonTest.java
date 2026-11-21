package solvela.draw.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.marketing.api.DrawRejectReason;
import solvela.marketing.api.DrawResultView;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住「哪些拒绝原因证明没抽」。上游的退还逻辑将来按它判，判错一个就是退错一批奖的成本。
 */
class DrawRejectReasonTest {

    @Test
    @DisplayName("🔴 只有 DUPLICATE_REQUEST 不证明没抽 —— 第一次可能正在抽或已经抽完")
    void 只有重复请求不证明没抽() {
        Set<DrawRejectReason> notProven = Arrays.stream(DrawRejectReason.values())
                .filter(r -> !r.provesNotDrawn())
                .collect(Collectors.toSet());

        assertThat(notProven).containsExactly(DrawRejectReason.DUPLICATE_REQUEST);
    }

    @Test
    @DisplayName("加了方法之后 JSON 仍是枚举名 —— 它在跨进程契约 DrawResultView 里，形状不能变")
    void JSON形状不变() {
        String json = JsonMapper.builder().build()
                .writeValueAsString(DrawResultView.ofReject(DrawRejectReason.DUPLICATE_REQUEST));

        assertThat(json).contains("\"reject\":\"DUPLICATE_REQUEST\"").doesNotContain("provesNotDrawn");
    }
}
