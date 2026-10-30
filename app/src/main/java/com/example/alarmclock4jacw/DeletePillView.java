package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * 左滑删除区。
 *
 * <p>背景使用和闹钟卡片完全相同的 8% 白玻璃色，铺满露出来的整段，所以删除区
 * 和卡片是同一层，不会出现一大块突兀的实心红。红色只是里面的一枚删除按钮：
 * 刚露出时是圆形，继续左滑会像胶囊一样变长，白色垃圾桶始终居中。</p>
 */
public class DeletePillView extends View {

    /** 与 bg_row_ripple 完全相同的淡白玻璃色。 */
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 红色删除按钮。 */
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable icon;

    private final float verticalInset;
    private final float endMargin;
    private final float maxButtonHeight;
    private final float growStart;
    private final float cornerRadius;
    private final float iconSize;

    /** -1 让第一次 setRevealed(0) 也走一遍可见性初始化。 */
    private float revealed = -1f;
    private final Rect iconBounds = new Rect();

    public DeletePillView(Context context) {
        this(context, null);
    }

    public DeletePillView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeletePillView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        glassPaint.setColor(ContextCompat.getColor(context, R.color.row_glass));
        buttonPaint.setColor(ContextCompat.getColor(context, R.color.danger));

        verticalInset = dp(8f);
        endMargin = dp(12f);
        // 删除按钮初始是一枚 72dp 的圆；继续左滑只横向变长，高度不跟着涨
        maxButtonHeight = dp(72f);
        // 和 SwipeRevealLayout 的 revealWidth 一致：停住时刚好是一枚圆按钮，
        // 继续左滑才从圆拉成胶囊。
        growStart = dp(92f);
        cornerRadius = dp(40f);
        iconSize = dp(28f);

        icon = ContextCompat.getDrawable(context, R.drawable.ic_delete);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(Color.WHITE);
        }
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }

    /** @param width 已经滑出来的宽度（像素）。0 表示完全收起，不绘制。 */
    public void setRevealed(float width) {
        float next = Math.max(0f, Math.min(width, getWidth()));
        if (next == revealed) return;
        revealed = next;
        boolean shown = revealed > 0f;
        setImportantForAccessibility(shown
                ? IMPORTANT_FOR_ACCESSIBILITY_YES
                : IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(shown);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (revealed <= 0f || w <= 0 || h <= 0) return;

        float revealLeft = w - revealed;

        // 1. 删除区背景：和闹钟卡片同色的玻璃层，整段铺满
        canvas.drawRect(revealLeft, 0, w, h, glassPaint);

        // 2. 红色按钮：初始是一枚真正的圆形按钮；滑过停靠点后横向变长
        float buttonHeight = Math.min(maxButtonHeight, h - verticalInset * 2f);
        float top = (h - buttonHeight) / 2f;
        float bottom = top + buttonHeight;
        float badgeWidth = Math.min(
                revealed - endMargin,
                buttonHeight + Math.max(0f, revealed - growStart));
        if (badgeWidth <= 0f) return;
        float right = w - endMargin;
        float left = right - badgeWidth;
        float radius = Math.min(cornerRadius, buttonHeight / 2f);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, buttonPaint);

        // 3. 白色垃圾桶，始终在红色按钮中心
        if (icon != null && badgeWidth >= iconSize * 1.25f) {
            float cx = (left + right) / 2f;
            float cy = (top + bottom) / 2f;
            int half = Math.round(iconSize / 2f);
            iconBounds.set(Math.round(cx) - half, Math.round(cy) - half,
                    Math.round(cx) + half, Math.round(cy) + half);
            icon.setBounds(iconBounds);
            icon.draw(canvas);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
