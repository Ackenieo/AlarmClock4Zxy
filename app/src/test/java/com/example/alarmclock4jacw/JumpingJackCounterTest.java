package com.example.alarmclock4jacw;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 开合跳计数状态机的单元测试：用合成骨骼点喂进去，验证计数、防抖、滞回。
 * 坐标全部是 0~1 归一化值，与线上一致。
 */
public class JumpingJackCounterTest {

    private static final long FRAME_MS = 66L;

    /**
     * 构造一个能产生指定 armScore / legScore 的姿势。
     * 肩线 y=0.40、髋线 y=0.60 => 躯干 0.20；肩宽 0.11 => 参考宽度 0.11。
     */
    private static Landmarks pose(float armScore, float legScore) {
        Landmarks lm = new Landmarks();
        lm.any = true;

        float shoulderY = 0.40f;
        float hipY = 0.60f;
        float torso = 0.20f;
        float shoulderW = 0.11f;

        lm.x[11] = 0.50f - shoulderW / 2f;
        lm.y[11] = shoulderY;
        lm.x[12] = 0.50f + shoulderW / 2f;
        lm.y[12] = shoulderY;

        lm.x[23] = 0.46f;
        lm.y[23] = hipY;
        lm.x[24] = 0.54f;
        lm.y[24] = hipY;

        lm.x[0] = 0.50f;
        lm.y[0] = shoulderY - 0.15f;

        float wristY = shoulderY - armScore * torso;
        lm.x[15] = 0.45f;
        lm.y[15] = wristY;
        lm.x[16] = 0.55f;
        lm.y[16] = wristY;

        lm.x[13] = 0.44f;
        lm.y[13] = (shoulderY + wristY) / 2f;
        lm.x[14] = 0.56f;
        lm.y[14] = (shoulderY + wristY) / 2f;

        float ankleDx = legScore * Math.max(shoulderW, 0.55f * torso);
        lm.x[27] = 0.50f - ankleDx / 2f;
        lm.y[27] = 0.90f;
        lm.x[28] = 0.50f + ankleDx / 2f;
        lm.y[28] = 0.90f;

        lm.x[25] = 0.46f;
        lm.y[25] = 0.75f;
        lm.x[26] = 0.54f;
        lm.y[26] = 0.75f;

        for (int i = 0; i < Landmarks.COUNT; i++) {
            lm.visible[i] = true;
        }
        return lm;
    }

    private static final Landmarks CLOSED = pose(-0.90f, 0.30f);
    private static final Landmarks OPEN = pose(0.90f, 1.20f);

    /** 往计数器里连续喂 frames 帧同一种姿势 */
    private static long feed(JumpingJackCounter c, Landmarks lm, long t, int frames) {
        for (int i = 0; i < frames; i++) {
            c.update(lm, t);
            t += FRAME_MS;
        }
        return t;
    }

    /** 完整做一次开合跳：并拢 -> 张开 -> 并拢 */
    private static long oneRep(JumpingJackCounter c, long t) {
        t = feed(c, CLOSED, t, 6);
        t = feed(c, OPEN, t, 6);
        t = feed(c, CLOSED, t, 6);
        return t;
    }

    @Test
    public void countsCleanReps() {
        JumpingJackCounter c = new JumpingJackCounter(20);
        long t = 0;
        for (int i = 0; i < 5; i++) t = oneRep(c, t);
        assertEquals(5, c.getCount());
    }

    @Test
    public void doesNotCountArmRaisesWithoutLegSpread() {
        JumpingJackCounter c = new JumpingJackCounter(20);
        Landmarks armsOnlyOpen = pose(0.90f, 0.40f);
        long t = 0;
        for (int i = 0; i < 5; i++) {
            t = feed(c, CLOSED, t, 6);
            t = feed(c, armsOnlyOpen, t, 6);
            t = feed(c, CLOSED, t, 6);
        }
        assertEquals(0, c.getCount());
    }

    @Test
    public void doesNotCountLegSpreadWithoutArmRaise() {
        JumpingJackCounter c = new JumpingJackCounter(20);
        Landmarks legsOnlyOpen = pose(-0.50f, 1.30f);
        long t = 0;
        for (int i = 0; i < 5; i++) {
            t = feed(c, CLOSED, t, 6);
            t = feed(c, legsOnlyOpen, t, 6);
            t = feed(c, CLOSED, t, 6);
        }
        assertEquals(0, c.getCount());
    }

    /** 卡在阈值之间的抖动不应该被计数（滞回区间） */
    @Test
    public void ignoresJitterAroundThresholds() {
        JumpingJackCounter c = new JumpingJackCounter(20);
        Landmarks borderlineHigh = pose(0.60f, 0.95f);
        Landmarks borderlineLow = pose(0.20f, 0.75f);
        long t = 0;
        for (int i = 0; i < 40; i++) {
            t = feed(c, (i % 2 == 0) ? borderlineHigh : borderlineLow, t, 1);
        }
        assertEquals(0, c.getCount());
    }

    /** 只在张开状态停留一两帧（没真做完整动作）不算 */
    @Test
    public void requiresHeldOpenPhase() {
        JumpingJackCounter c = new JumpingJackCounter(20);
        long t = 0;
        for (int i = 0; i < 4; i++) {
            t = feed(c, CLOSED, t, 10);
            t = feed(c, OPEN, t, 1);   // 只闪过一帧
            t = feed(c, CLOSED, t, 10);
        }
        assertEquals(0, c.getCount());
    }

    @Test
    public void reachesTarget() {
        JumpingJackCounter c = new JumpingJackCounter(3);
        long t = 0;
        for (int i = 0; i < 3; i++) t = oneRep(c, t);
        assertEquals(3, c.getCount());
        assertEquals(3, c.getTarget());
    }

    @Test
    public void reportsInvalidWhenPoseMissing() {
        JumpingJackCounter c = new JumpingJackCounter(10);
        JumpingJackCounter.Frame f = c.update(null, 0);
        assertEquals(false, f.valid);
        assertEquals("no_person", f.hint);
    }
}
