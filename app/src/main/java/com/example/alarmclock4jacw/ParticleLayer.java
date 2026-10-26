package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 特效 UI：点击粒子。
 *
 * 铺在整页最上层，但完全不接管触摸 —— 只是被 Activity 的 dispatchTouchEvent 喂坐标，
 * 在按下的位置炸开一圈光点，受重力往下掉、慢慢淡出。
 *
 * 自己算生命周期：没有粒子存活时不再请求重绘，静止时零开销。
 */
public class ParticleLayer extends View {

    private static final int MAX = 96;
    private static final int PER_BURST = 14;
    private static final float GRAVITY = 260f;

    private final float[] px = new float[MAX];
    private final float[] py = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final float[] life = new float[MAX];
    private final float[] life0 = new float[MAX];
    private final float[] size = new float[MAX];

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int accent;

    private int count;
    private long lastFrame;

    public ParticleLayer(Context context) {
        this(context, null);
    }

    public ParticleLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = SkinStore.accent(context);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** 在某点炸一簇粒子。坐标是相对本 View（也就是整页）的。 */
    public void burst(float x, float y) {
        for (int i = 0; i < PER_BURST && count < MAX; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 90f + random.nextFloat() * 260f;

            px[count] = x;
            py[count] = y;
            vx[count] = (float) Math.cos(angle) * speed;
            vy[count] = (float) Math.sin(angle) * speed - 60f;   // 稍微往上抛一点
            // 存活 0.55~1.1 秒：太短会像闪一下，太长会糊住后面的内容
            life[count] = 0.55f + random.nextFloat() * 0.55f;
            life0[count] = life[count];
            size[count] = dp(1.8f + random.nextFloat() * 2.4f);
            count++;
        }
        lastFrame = 0;
        invalidate();
    }

    /** 在自己的范围里随便找个点炸一簇，给皮肤市场的小预览用。 */
    public void burstRandom() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        burst(w * (0.30f + random.nextFloat() * 0.40f),
                h * (0.32f + random.nextFloat() * 0.36f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count == 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int write = 0;
        for (int i = 0; i < count; i++) {
            life[i] -= dt;
            if (life[i] <= 0f) continue;

            vy[i] += GRAVITY * dt;
            vx[i] *= (1f - 2.2f * dt);           // 空气阻力
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;

            float f = life[i] / life0[i];
            paint.setColor(alpha(accent, 0.95f * f));
            canvas.drawCircle(px[i], py[i], size[i] * f, paint);

            // 就地压缩数组，把已经死掉的粒子挤出去
            if (write != i) {
                px[write] = px[i];
                py[write] = py[i];
                vx[write] = vx[i];
                vy[write] = vy[i];
                life[write] = life[i];
                life0[write] = life0[i];
                size[write] = size[i];
            }
            write++;
        }
        count = write;

        if (count > 0) {
            postInvalidateOnAnimation();
        } else {
            lastFrame = 0;
        }
    }

    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
