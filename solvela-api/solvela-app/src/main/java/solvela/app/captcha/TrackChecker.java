package solvela.app.captcha;

/**
 * 拖动轨迹判别：这一下是不是人手拖出来的。纯函数，不碰 Redis。
 *
 * <h3>轨迹从哪来</h3>
 * 前端在拖动时把每个 pointermove 记成一个点 {@code [t, x, y]}：
 * t 是距按下的毫秒数，x 是拼图左边缘在<b>原图坐标</b>里的位置（和提交的答案同一坐标系），
 * y 是手指/鼠标相对按下时的纵向偏移（显示 px，只用来看有没有抖）。
 *
 * <h3>规则故意定得宽</h3>
 * 只拦「明显不是人」的：直接提交一个 x、一步瞬移到位、匀速直线。
 * 宁可放过模仿得好的脚本，也不误伤手快、手稳的真人 —— 误伤的代价是用户永远过不去。
 * 轨迹是客户端自报的，能伪造；这一层和出图一样，只是抬高批量成本。
 *
 * @Date 2026-09-28
 */
public final class TrackChecker {

    /** 点数上限：前端按 16ms 抽稀并封顶，正常拖动远到不了；超过就是在塞大包 */
    public static final int MAX_POINTS = 400;

    private static final int MIN_POINTS = 8;

    private static final long MIN_DURATION_MS = 200;

    private static final long MAX_DURATION_MS = 20_000;

    /** 速度的变异系数低于它算「匀速」。人拖动有加减速，通常远高于 0.3 */
    private static final double MIN_SPEED_CV = 0.1;

    private TrackChecker() {
    }

    /**
     * @param track   轨迹点 {@code [t, x, y]}
     * @param finalX  提交的答案 x
     * @param slack   轨迹终点与答案之间允许的误差（原图像素）
     * @return 不像人的原因（只进日志，绝不回给客户端）；像人返回 null
     */
    public static String reject(int[][] track, int finalX, int slack) {
        if (track == null || track.length < MIN_POINTS) {
            return "too-few-points";
        }
        if (track.length > MAX_POINTS) {
            return "too-many-points";
        }
        for (int[] point : track) {
            if (point == null || point.length != 3) {
                return "malformed";
            }
        }
        for (int i = 1; i < track.length; i++) {
            if (track[i][0] < track[i - 1][0]) {
                return "time-goes-back";
            }
        }
        long duration = (long) track[track.length - 1][0] - track[0][0];
        if (duration < MIN_DURATION_MS) {
            return "too-fast";
        }
        if (duration > MAX_DURATION_MS) {
            return "too-slow";
        }
        if (Math.abs(track[track.length - 1][1] - finalX) > slack) {
            return "end-mismatch";
        }
        if (yNeverMoves(track)) {
            return "no-y-jitter";
        }
        if (uniformSpeed(track)) {
            return "uniform-speed";
        }
        return null;
    }

    /** 人手横着拖，纵向总会抖几个像素；脚本的 y 常常一动不动 */
    private static boolean yNeverMoves(int[][] track) {
        int y = track[0][2];
        for (int[] point : track) {
            if (point[2] != y) {
                return false;
            }
        }
        return true;
    }

    /** 各段速度几乎一样 = 匀速直线。人会先快后慢、对准时停顿 */
    private static boolean uniformSpeed(int[][] track) {
        double sum = 0;
        double sumSq = 0;
        int n = 0;
        for (int i = 1; i < track.length; i++) {
            int dt = track[i][0] - track[i - 1][0];
            if (dt <= 0) {
                continue;
            }
            double v = (double) (track[i][1] - track[i - 1][1]) / dt;
            sum += v;
            sumSq += v * v;
            n++;
        }
        if (n < 3) {
            return true;
        }
        double mean = sum / n;
        if (Math.abs(mean) < 1e-9) {
            return false;
        }
        double variance = Math.max(0, sumSq / n - mean * mean);
        return Math.sqrt(variance) / Math.abs(mean) < MIN_SPEED_CV;
    }
}
