package net.lab1024.sa.base.module.support.file.domain;

/**
 * 图片变体。素材库列表要展示缩略图 —— 50 张原图每张 3MB 就是 150MB 流量，页面直接卡死，
 * 这不是优化是能不能用的问题。
 *
 * @param width  目标宽度，0 表示不限
 * @param height 目标高度，0 表示不限
 * @Date 2026-08-10
 */
public record ImageVariant(int width, int height) {

    /**
     * 原图，不做任何处理。
     */
    public static final ImageVariant ORIGINAL = new ImageVariant(0, 0);

    /**
     * 列表缩略图。200px 宽足够素材库网格展示，再大没有收益。
     */
    public static final ImageVariant THUMBNAIL = new ImageVariant(200, 0);

    public ImageVariant {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("图片变体尺寸不能为负：" + width + "x" + height);
        }
    }

    public static ImageVariant ofWidth(int width) {
        return new ImageVariant(width, 0);
    }

    public boolean isOriginal() {
        return width == 0 && height == 0;
    }
}
