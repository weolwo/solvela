package solvela.base.module.support.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片相关配置：分类级尺寸约束 + 云端图片处理模板。
 *
 * <p><b>为什么约束放在配置而不是分类表</b>：分类表刻意精简成 code/name/tag/sort 四个字段。
 * 尺寸规则改动频率极低（改一次要通知运营重新出图），而且它和「运营能自助配的东西」
 * 不是一回事。真到需要运营自助配的那天，加一个 {@code config json} 列即可，不用加五列。
 *
 * <pre>{@code
 * file:
 *   storage:
 *     image:
 *       # 云端图片处理模板。占位符 {w} {h} 会被替换；留空 = 不做任何处理，一律返回原图
 *       # 阿里云 OSS : "?x-oss-process=image/resize,w_{w}"
 *       # 腾讯云 COS : "?imageMogr2/thumbnail/{w}x"
 *       process-template: ""
 *       rules:
 *         BANNER:      { width: 1920, height: 640, max-size-kb: 2048 }
 *         GOODS_MAIN:  { min-width: 800, min-height: 800, ratio: "1:1" }
 * }</pre>
 *
 * @Date 2026-08-10
 */
@Data
@Component
@ConfigurationProperties(prefix = "file.storage.image")
public class FileImageProperties {

    /**
     * 云端图片处理 URL 模板。
     *
     * <p><b>刻意做成模板而不是硬编码 {@code x-oss-process}</b>：那是阿里云 OSS 的私有扩展，
     * <b>通用 S3 协议本身没有图片处理能力</b> —— MinIO 和 AWS S3 都不认这个参数，
     * 硬编码上去只会产出一个 400。腾讯云 COS 又是另一套语法。
     *
     * <p>留空（默认）时所有变体都返回原图 URL。本地存储模式同理 ——
     * 它的定位是开发与私有化单机，为它写一套图片处理管线不划算。
     */
    private String processTemplate = "";

    /**
     * 分类 code → 图片约束。没配的分类不做尺寸校验。
     */
    private Map<String, Rule> rules = new LinkedHashMap<>();

    public Rule ruleOf(String categoryCode) {
        return categoryCode == null ? null : rules.get(categoryCode);
    }

    /**
     * 单个分类的图片约束。<b>全部字段可选</b>，只校验配了的那些。
     */
    @Data
    public static class Rule {

        /**
         * 强制宽度。banner 必须 1920×640 这类场景 —— <b>必须在上传时拦</b>，
         * 等发布后才发现变形就晚了。
         */
        private Integer width;

        private Integer height;

        private Integer minWidth;

        private Integer minHeight;

        /**
         * 宽高比，形如 {@code 16:9} / {@code 1:1}。
         */
        private String ratio;

        /**
         * 分类级大小上限，比全局的更严。
         */
        private Integer maxSizeKb;
    }
}
