package solvela.app.captcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 轨迹判别。纯单元测试。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 像人的轨迹放行 —— 误伤的表现是「用户怎么拖都过不去」；</li>
 *   <li>🔴 只交 x、瞬移、匀速直线、y 纹丝不动 → 拒；</li>
 *   <li>点数封顶，防塞大包。</li>
 * </ul>
 */
public class TrackCheckerTest {

    /** 模拟人手：先加速后减速（ease-out）、y 抖几像素、结尾拖过头再回拉 */
    public static int[][] humanTrack(int targetX) {
        int n = 40;
        int[][] track = new int[n + 2][];
        for (int i = 0; i <= n; i++) {
            double p = (double) i / n;
            double eased = 1 - Math.pow(1 - p, 3);
            track[i] = new int[] {i * 16 + (i % 3), (int) Math.round(eased * (targetX + 3)), (i * 7 % 5) - 2};
        }
        track[n + 1] = new int[] {n * 16 + 90, targetX, 1};
        return track;
    }

    @Test
    @DisplayName("🔴 像人的轨迹放行")
    void 人手放行() {
        assertNull(TrackChecker.reject(humanTrack(137), 137, 5));
    }

    @Test
    @DisplayName("🔴 没有轨迹 / 点太少 → 拒（只交一个 x 的脚本）")
    void 没轨迹() {
        assertEquals("too-few-points", TrackChecker.reject(null, 137, 5));
        assertEquals("too-few-points", TrackChecker.reject(new int[][] {{0, 0, 0}, {300, 137, 1}}, 137, 5));
    }

    @Test
    @DisplayName("🔴 匀速直线 → 拒")
    void 匀速() {
        int[][] track = new int[30][];
        for (int i = 0; i < 30; i++) {
            track[i] = new int[] {i * 16, i * 5, i % 2};
        }
        assertEquals("uniform-speed", TrackChecker.reject(track, 145, 5));
    }

    @Test
    @DisplayName("🔴 y 纹丝不动 → 拒")
    void y不动() {
        int[][] track = humanTrack(137);
        for (int[] point : track) {
            point[2] = 0;
        }
        assertEquals("no-y-jitter", TrackChecker.reject(track, 137, 5));
    }

    @Test
    @DisplayName("太快（一瞬间拖完）、终点对不上答案 → 拒")
    void 太快与终点不符() {
        int[][] fast = humanTrack(137);
        for (int i = 0; i < fast.length; i++) {
            fast[i][0] = i;
        }
        assertEquals("too-fast", TrackChecker.reject(fast, 137, 5));
        assertEquals("end-mismatch", TrackChecker.reject(humanTrack(80), 137, 5));
    }

    @Test
    @DisplayName("时间倒流、点格式不对、点数超上限 → 拒")
    void 畸形() {
        int[][] back = humanTrack(137);
        back[5][0] = 0;
        assertEquals("time-goes-back", TrackChecker.reject(back, 137, 5));

        int[][] malformed = humanTrack(137);
        malformed[3] = new int[] {1, 2};
        assertEquals("malformed", TrackChecker.reject(malformed, 137, 5));

        assertEquals("too-many-points",
                TrackChecker.reject(new int[TrackChecker.MAX_POINTS + 1][], 137, 5));
    }
}
