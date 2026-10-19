package com.example.alarmclock4jacw;

/**
 * 开合跳计数器（纯逻辑，不依赖 Android UI 与 ML Kit，方便单独验证）。
 *
 * 输入是 {@link Landmarks}（0~1 归一化坐标），所以离镜头远近、人高矮都不影响判定：
 *
 *   armScore = (肩线 y - 双腕 y) / 躯干长度
 *       手臂自然下垂约 -1.0，举过头顶约 +0.7 ~ +1.1
 *   legScore = |左踝 x - 右踝 x| / 参考宽度
 *       参考宽度取 max(肩宽, 0.55 * 躯干长)，侧身时肩宽会塌缩，用躯干兜底
 *       并腿约 0.1 ~ 0.5，开腿约 0.9 ~ 1.3
 *
 * 计一次数的条件是"张开 -> 并拢"的完整状态迁移，并要求：
 *   1) 候选状态连续保持 HOLD_MS 才采纳（抑制抖动，约等于 2~3 帧）；
 *   2) 张开状态至少持续 MIN_OPEN_MS；
 *   3) 两次计数间隔不小于 MIN_REP_GAP_MS。
 * "进入张开"与"回到并拢"用两套不同阈值（滞回），避免在临界值反复横跳重复计数。
 *
 * 阈值是用同一套 33 点模型在测试视频（75s 连续开合跳）上跑出来的：
 * 0.55 / 0.90 落在稳定区间，既不会漏掉正常幅度，也不会把"手臂平举""慢动作示范"算进去。
 * 注意：这里刻意不做指数平滑——EMA 会滞后于每次动作的峰值，实测会把手臂得分削掉 30% 以上导致漏计。
 */
public class JumpingJackCounter {

    public enum Phase {UNKNOWN, CLOSED, OPEN}

    public static class Frame {
        public boolean valid;
        public String hint = "";
        public Phase phase = Phase.UNKNOWN;
        public int count;
        public boolean countedNow;
        public float armScore;
        public float legScore;
        public boolean tooClose;
    }

    /** 进入"张开"的阈值 */
    private static final float ARM_OPEN = 0.55f;
    private static final float LEG_OPEN = 0.90f;
    /** 回到"并拢"的阈值 */
    private static final float ARM_CLOSED = 0.05f;
    private static final float LEG_CLOSED = 0.60f;

    private static final long HOLD_MS = 100L;
    private static final long MIN_OPEN_MS = 180L;
    private static final long MIN_REP_GAP_MS = 320L;
    private static final long LOST_RESET_MS = 800L;

    /** 躯干在画面里至少要占这么高，否则判定为"人太远/没站好" */
    private static final float TORSO_MIN = 0.05f;
    /** 躯干超过这个高度说明离镜头太近，容易出画 */
    private static final float TORSO_TOO_CLOSE = 0.55f;

    private final int target;
    private int count;

    private Phase phase = Phase.UNKNOWN;
    private Phase pending = Phase.UNKNOWN;
    private long pendingSince = 0L;
    private long openSince = 0L;
    private long lastRepAt = -100000L;
    private long lastValidAt = 0L;

    private final Frame out = new Frame();

    public JumpingJackCounter(int target) {
        this.target = Math.max(1, target);
    }

    public int getTarget() {
        return target;
    }

    public int getCount() {
        return count;
    }

    public Phase getPhase() {
        return phase;
    }

    public void reset() {
        count = 0;
        phase = Phase.UNKNOWN;
        pending = Phase.UNKNOWN;
        pendingSince = 0L;
        openSince = 0L;
        lastRepAt = -100000L;
        lastValidAt = 0L;
    }

    public Frame update(Landmarks lm, long nowMs) {
        out.countedNow = false;
        out.count = count;
        out.phase = phase;

        if (lm == null || !lm.any) return invalid(nowMs, "no_person");

        if (!lm.ok(11) || !lm.ok(12) || !lm.ok(23) || !lm.ok(24)) return invalid(nowMs, "stand");

        float shoulderY = (lm.y[11] + lm.y[12]) / 2f;
        float hipY = (lm.y[23] + lm.y[24]) / 2f;
        float torso = hipY - shoulderY;
        if (torso < TORSO_MIN) return invalid(nowMs, "stand");

        float shoulderW = Math.abs(lm.x[11] - lm.x[12]);
        float ref = Math.max(shoulderW, 0.55f * torso);

        float wristY;
        if (lm.ok(15) && lm.ok(16)) {
            wristY = (lm.y[15] + lm.y[16]) / 2f;
        } else if (lm.ok(15)) {
            wristY = lm.y[15];
        } else if (lm.ok(16)) {
            wristY = lm.y[16];
        } else {
            return invalid(nowMs, "arms");
        }

        if (!lm.ok(27) || !lm.ok(28)) return invalid(nowMs, "feet");

        float arm = (shoulderY - wristY) / torso;
        float leg = Math.abs(lm.x[27] - lm.x[28]) / ref;

        lastValidAt = nowMs;

        Phase candidate;
        if (arm >= ARM_OPEN && leg >= LEG_OPEN) {
            candidate = Phase.OPEN;
        } else if (arm <= ARM_CLOSED && leg <= LEG_CLOSED) {
            candidate = Phase.CLOSED;
        } else {
            candidate = Phase.UNKNOWN; // 过渡帧，不参与判定
        }

        if (candidate == Phase.UNKNOWN || candidate == phase) {
            // 过渡帧，或者本来就处于该状态：清掉待定项，避免"陈旧待定"跨帧累积后误计数
            pending = Phase.UNKNOWN;
        } else if (candidate != pending) {
            pending = candidate;
            pendingSince = nowMs;
        } else if (nowMs - pendingSince >= HOLD_MS) {
            Phase prev = phase;
            phase = candidate;
            pending = Phase.UNKNOWN;
            if (candidate == Phase.OPEN) {
                openSince = nowMs;
            } else if (prev == Phase.OPEN) {
                long openDuration = nowMs - openSince;
                if (openDuration >= MIN_OPEN_MS && nowMs - lastRepAt >= MIN_REP_GAP_MS) {
                    count++;
                    lastRepAt = nowMs;
                    out.countedNow = true;
                }
            }
        }

        out.valid = true;
        out.hint = "";
        out.phase = phase;
        out.count = count;
        out.armScore = arm;
        out.legScore = leg;
        out.tooClose = torso > TORSO_TOO_CLOSE;
        return out;
    }

    private Frame invalid(long nowMs, String hint) {
        out.valid = false;
        out.hint = hint;
        out.count = count;
        out.phase = phase;
        out.countedNow = false;
        pending = Phase.UNKNOWN;
        // 长时间丢失人体后回到未知状态，避免"张开->丢失->并拢"被误判成一次
        if (nowMs - lastValidAt > LOST_RESET_MS) {
            phase = Phase.UNKNOWN;
        }
        return out;
    }
}
