package solvela.app.captcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑块出图。纯单元测试，不起容器。
 *
 * <p>🔴 顺带钉住「没有用到字体」：本地有字体、生产（distroless）没有，
 * 一旦有人往背景上加 drawString，这里不会红，但生产会炸 —— 所以这条规矩写在类注释里，
 * 这里能做的是保证出图本身在 headless 下跑得通。
 */
class SliderCaptchaGeneratorTest {

    private final SliderCaptchaGenerator generator = new SliderCaptchaGenerator();

    private static BufferedImage decode(String base64) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    @Test
    @DisplayName("两张图都是合法 PNG，尺寸与声明一致")
    void 出图合法() throws Exception {
        SliderCaptchaGenerator.Puzzle puzzle = generator.generate();

        BufferedImage bg = decode(puzzle.background());
        BufferedImage piece = decode(puzzle.piece());
        assertEquals(SliderCaptchaGenerator.WIDTH, bg.getWidth());
        assertEquals(SliderCaptchaGenerator.HEIGHT, bg.getHeight());
        assertEquals(SliderCaptchaGenerator.PIECE, piece.getWidth());
        assertEquals(SliderCaptchaGenerator.PIECE, piece.getHeight());
        assertTrue(piece.getColorModel().hasAlpha(), "拼图要透明底，否则前端叠上去是一个方块");
    }

    @RepeatedTest(50)
    @DisplayName("缺口永远整块落在图内，且不贴着最左边（否则不用拖就「对了」）")
    void 缺口位置合法() {
        SliderCaptchaGenerator.Puzzle puzzle = generator.generate();

        assertTrue(puzzle.answerX() >= 40, "缺口太靠左，拼图起点就在答案附近：" + puzzle.answerX());
        assertTrue(puzzle.answerX() + SliderCaptchaGenerator.PIECE <= SliderCaptchaGenerator.WIDTH);
        assertTrue(puzzle.pieceY() >= 0);
        assertTrue(puzzle.pieceY() + SliderCaptchaGenerator.PIECE <= SliderCaptchaGenerator.HEIGHT);
    }

    @Test
    @DisplayName("每张图都不一样 —— 否则可以建图库查表")
    void 每张不同() {
        assertNotEquals(generator.generate().background(), generator.generate().background());
    }
}
