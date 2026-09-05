package solvela.mall.constant;

import tools.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import solvela.base.json.JsonUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SKU 规格 JSON 的解析。<b>运营侧与 C 端共用这一份</b>。
 *
 * <p>2026-09-05 从 {@code MallCommodityService} 提出来 —— C 端门面也要解析同一份 JSON，
 * 各写一遍的代价是「后台看到的规格和 C 端看到的不一样」，
 * 而这类不一致只有在某个 SKU 的 JSON 脏了的时候才暴露。
 *
 * <p>用 {@code LinkedHashMap} 而不是普通 Map：<b>键的顺序就是运营配的顺序</b>，
 * C 端要按它决定「颜色在上还是尺码在上」。换成 HashMap 之后顺序会变得不可预测，
 * 而且是那种「本地看着对、换台机器就不对」的不可预测。
 */
@Slf4j
public final class MallSkuAttrs {

    private MallSkuAttrs() {
    }

    /**
     * 单个 SKU 的脏 JSON <b>不该让整个商品打不开</b>，故解析失败降级为空规格并留一条日志。
     */
    public static Map<String, String> parse(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> attrs = JsonUtils.parseType(json,
                    new TypeReference<LinkedHashMap<String, String>>() {
                    });
            return attrs == null ? new LinkedHashMap<>() : attrs;
        } catch (RuntimeException e) {
            log.warn("[商城] SKU 规格 JSON 解析失败，已降级为空规格: {}", json, e);
            return new LinkedHashMap<>();
        }
    }
}
