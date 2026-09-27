package solvela.app.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 滑块拼图的出图：一张背景（抠了一个缺口）+ 一块拼图。
 *
 * <h3>🔴 只画图形，不画一个字</h3>
 * 部署镜像是 distroless，里面<b>没有字体</b>。任何 {@code drawString} 都会在生产上炸（找不到 fontconfig），
 * 而本地开发机有字体，测试照样绿 —— 典型的「只在生产坏」。所以背景全是渐变、几何块和噪点。
 *
 * <h3>它挡得住谁</h3>
 * 挡的是「写个循环直接调接口」的脚本：不识别图就拿不到坐标，拿不到坐标就拿不到通行票。
 * 挡不住专门针对它写的破解（边缘检测找缺口）—— 自建滑块的上限就是抬高批量的成本，
 * 真要对抗专业黑产得换厂商（行为分析 + 跨客户数据）。见方案文档。
 *
 * @Date 2026-09-27
 */
@Component
public class SliderCaptchaGenerator {

    /** 背景尺寸（图像像素）。前端按比例缩放显示，提交的是图像坐标系里的 x */
    public static final int WIDTH = 300;

    public static final int HEIGHT = 160;

    /** 拼图主体边长与凸起半径 */
    private static final int SIZE = 44;

    private static final int KNOB = 8;

    /** 拼图画布（含凸起）的宽高 */
    public static final int PIECE = SIZE + KNOB;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @param background 背景 PNG（base64）
     * @param piece      拼图 PNG（base64，透明底）
     * @param answerX    缺口左边缘的 x —— 只进服务端，绝不下发
     * @param pieceY     拼图的 y（下发：前端要把拼图画在这一行上）
     */
    public record Puzzle(String background, String piece, int answerX, int pieceY) {
    }

    public Puzzle generate() {
        int x = SIZE + 20 + RANDOM.nextInt(WIDTH - PIECE - SIZE - 30);
        int y = 4 + RANDOM.nextInt(HEIGHT - PIECE - 8);

        BufferedImage background = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintScene(g);
        g.dispose();

        Shape shape = pieceShape();

        // 拼图：从背景上把缺口那块原样抠下来，描一圈亮边
        BufferedImage piece = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D p = piece.createGraphics();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setClip(shape);
        p.drawImage(background, -x, -y, null);
        p.setClip(null);
        p.setStroke(new BasicStroke(2f));
        p.setColor(new Color(255, 255, 255, 220));
        p.draw(shape);
        p.dispose();

        // 背景：缺口处压暗并描边
        Graphics2D b = background.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape hole = AffineTransform.getTranslateInstance(x, y).createTransformedShape(shape);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        b.setColor(Color.BLACK);
        b.fill(hole);
        b.setComposite(AlphaComposite.SrcOver);
        b.setStroke(new BasicStroke(1.5f));
        b.setColor(new Color(255, 255, 255, 160));
        b.draw(hole);
        b.dispose();

        return new Puzzle(png(background), png(piece), x, y);
    }

    /** 背景：斜向渐变 + 一堆半透明几何块 + 噪点。每张都不一样，没法建图库查表 */
    private static void paintScene(Graphics2D g) {
        Color from = randomColor(90, 200);
        Color to = randomColor(90, 200);
        g.setPaint(new GradientPaint(0, 0, from, WIDTH, HEIGHT, to));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < 14; i++) {
            Color c = randomColor(40, 240);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60 + RANDOM.nextInt(90)));
            int w = 20 + RANDOM.nextInt(90);
            int h = 20 + RANDOM.nextInt(70);
            int sx = RANDOM.nextInt(WIDTH) - 20;
            int sy = RANDOM.nextInt(HEIGHT) - 20;
            if (RANDOM.nextBoolean()) {
                g.fillOval(sx, sy, w, h);
            } else {
                g.rotate(Math.toRadians(RANDOM.nextInt(90) - 45), sx + w / 2.0, sy + h / 2.0);
                g.fillRoundRect(sx, sy, w, h, 12, 12);
                g.setTransform(new AffineTransform());
            }
        }
        for (int i = 0; i < 500; i++) {
            g.setColor(randomColor(0, 255));
            g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
        }
    }

    /** 拼图形状：圆角方块，上边和右边各鼓一个半圆。坐标以拼图画布为准 */
    private static Shape pieceShape() {
        Area area = new Area(new RoundRectangle2D.Double(0, KNOB, SIZE, SIZE, 8, 8));
        area.add(new Area(new Ellipse2D.Double(SIZE / 2.0 - KNOB, 0, KNOB * 2, KNOB * 2)));
        area.add(new Area(new Ellipse2D.Double(SIZE - KNOB, KNOB + SIZE / 2.0 - KNOB, KNOB * 2, KNOB * 2)));
        return area;
    }

    private static Color randomColor(int min, int max) {
        return new Color(min + RANDOM.nextInt(max - min + 1), min + RANDOM.nextInt(max - min + 1),
                min + RANDOM.nextInt(max - min + 1));
    }

    private static String png(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("滑块验证码出图失败", e);
        }
    }
}
