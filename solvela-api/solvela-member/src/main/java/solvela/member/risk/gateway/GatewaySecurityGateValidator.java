package solvela.member.risk.gateway;

import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigValueValidator;
import solvela.exception.BusinessException;
import solvela.security.KeywordRules;
import tools.jackson.databind.JsonNode;

/**
 * 后台保存 {@code gateway_security_gate} 之前的校验（网关方案 §5.3、§5.4）。
 *
 * <h3>🔴 关键字规则：任何一条命中反例集就拒绝保存</h3>
 * 用的是网关执行时的<b>同一份</b>编译与反例集（{@link KeywordRules}）。一条写宽了的规则上线，
 * 搜「Floor(落地灯)」的人会被封一天 —— 这种错只能在保存时拦，到了网关那边它已经在封人了。
 * 停用的规则也校验：启用它只需要改一个布尔，不该绕过这一步。
 *
 * <p>网关加载时还会再过一遍（有人绕过后台直接改库），过不了的那条丢掉并告警。
 *
 * <h3>蜜罐路径只查格式</h3>
 * 「与真实接口重叠」要看网关的路由表，由网关在启动与加载时判（重叠的启动失败 / 不启用）。
 *
 * @Date 2026-10-17
 */
@Component
public class GatewaySecurityGateValidator implements ConfigValueValidator {

    @Override
    public String configKey() {
        return ConfigKeyEnum.GATEWAY_SECURITY_GATE.getValue();
    }

    @Override
    public void validate(String configValue) {
        JsonNode root;
        try {
            root = JsonUtils.getMapper().readTree(configValue);
        } catch (Exception e) {
            throw new BusinessException("网关安全闸配置不是合法的 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException("网关安全闸配置必须是一个 JSON 对象");
        }
        JsonNode keywords = root.path("keywords");
        if (!keywords.isMissingNode() && !keywords.isArray()) {
            throw new BusinessException("keywords 必须是数组");
        }
        for (JsonNode k : keywords) {
            try {
                KeywordRules.compile(k.path("code").asString(null), k.path("scope").asString(null),
                        k.path("pattern").asString(null));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
        }
        JsonNode honeypots = root.path("honeypotPaths");
        if (!honeypots.isMissingNode() && !honeypots.isArray()) {
            throw new BusinessException("honeypotPaths 必须是数组");
        }
        for (JsonNode h : honeypots) {
            String code = h.path("code").asString("");
            String path = h.path("path").asString("");
            if (!code.matches("[A-Z0-9_]{1,32}") || !path.startsWith("/")) {
                throw new BusinessException("蜜罐路径 " + h + " 不合法：编码只能是 1~32 位大写字母、数字、下划线，路径以 / 开头");
            }
        }
    }
}
