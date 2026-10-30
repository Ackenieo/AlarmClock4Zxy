package com.example.alarmclock4jacw;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/**
 * 类 Apple 闹钟的左滑删除行，分两段防误删：
 *
 * <p><b>第一段（从收起状态左滑）</b>：不管手指划多远，行最多只滑到 92dp 的“露出
 * 删除图标”状态（图一），绝不会在这一段删除；松手后吸附到露出状态。</p>
 *
 * <p><b>第二段（已经在露出状态再左滑）</b>：此时继续左滑，浅色背景条上的红色圆形
 * 删除按钮会随手指向左探出更多，滑过删除阈值后松手才真正删除；没滑够则弹回露出
 * 状态。也可以直接点删除按钮删除。这样顺手一划只会露出按钮，不会误删。</p>
 */
public class SwipeRevealLayout extends ViewGroup {

    /** 当前展开的行，保证同一时间只有一行是打开的。 */
    private static SwipeRevealLayout openRow;

    private View content;
    private DeletePillView action;
    private View divider;

    private final int touchSlop;
    private final int revealWidth;
    private int deleteThreshold;

    private float offset;
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;
    /** 本次手势开始时行是否已经处在“露出删除按钮”的状态。 */
    private boolean fromOpen;
    private boolean deleting;
    private ValueAnimator animator;

    private Runnable deleteListener;
    private View.OnClickListener contentClickListener;

    public SwipeRevealLayout(Context context) {
        this(context, null);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        revealWidth = dp(92);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        content = findViewById(R.id.row_content);
        action = findViewById(R.id.delete_action);
        divider = getChildCount() > 2 ? getChildAt(2) : null;

        if (action != null) {
            action.setOnClickListener(v -> {
                if (!deleting) commitDelete();
            });
        }
        if (content != null) {
            content.setOnClickListener(v -> {
                if (offset > 0) {
                    // 展开状态下先收起，不触发编辑
                    animateTo(0);
                    return;
                }
                if (contentClickListener != null) {
                    contentClickListener.onClick(this);
                }
            });
        }
    }

    public void setOnContentClickListener(View.OnClickListener listener) {
        contentClickListener = listener;
    }

    public void setOnDeleteListener(Runnable listener) {
        deleteListener = listener;
    }

    /**
     * 只给内容层调透明度。
     * 禁用状态的闹钟整行变灰，但删除按钮是操作入口 —— 跟着一起灰掉会看着像坏了，
     * 所以灰只压内容层，淡白背景条与红色删除标记保持原色。
     */
    public void setContentAlpha(float alpha) {
        if (content != null) content.setAlpha(alpha);
    }

    /** 收起当前展开的行（列表一开始滚动就调用）。 */
    public static void closeOpenRow() {
        if (openRow != null) openRow.animateTo(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = 0;

        if (content != null) {
            measureChild(content,
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    heightMeasureSpec);
            height = content.getMeasuredHeight();
        }

        if (action != null) {
            action.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        if (divider != null) {
            measureChild(divider, widthMeasureSpec, heightMeasureSpec);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = getWidth();
        int height = getHeight();

        if (content != null) content.layout(0, 0, width, height);
        if (action != null) action.layout(0, 0, width, height);
        if (divider != null) {
            int dh = divider.getMeasuredHeight();
            divider.layout(0, height - dh, width, height);
        }

        deleteThreshold = Math.max(revealWidth + dp(72), Math.round(width * 0.58f));
        applyOffset();
    }

    private void applyOffset() {
        if (content != null) content.setTranslationX(-offset);
        if (action != null) action.setRevealed(offset);
    }

    private void setOffset(float value) {
        offset = Math.max(0f, Math.min(getWidth(), value));
        applyOffset();
    }

    /** 打开到“露出图标”的宽度，或者回 0。 */
    private void animateTo(float target) {
        if (deleting) return;
        if (animator != null) animator.cancel();
        if (target > 0 && openRow != null && openRow != this) {
            openRow.animateTo(0);
        }
        float start = offset;
        animator = ValueAnimator.ofFloat(start, target);
        animator.setDuration(200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> setOffset((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (target > 0) {
                    openRow = SwipeRevealLayout.this;
                } else if (openRow == SwipeRevealLayout.this) {
                    openRow = null;
                }
            }
        });
        animator.start();
    }

    /**
     * 滑过阈值后按当前滑出的宽度直接删除。
     * 不再补一段“铺满整行”的动画：红色区域应当停在手指松开时的长度。
     */
    private void commitDelete() {
        if (deleting || deleteListener == null) return;
        deleting = true;
        if (animator != null) animator.cancel();
        if (openRow == this) openRow = null;
        deleteListener.run();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (deleting) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                fromOpen = offset > revealWidth * 0.5f;
                if (animator != null) animator.cancel();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    lastX = ev.getX();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (deleting) return true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                fromOpen = offset > revealWidth * 0.5f;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - lastX;
                lastX = ev.getX();
                float target = offset - dx;
                // 第一段（从收起状态左滑）：最多只能划到露出图标，划再多也越不过去，
                // 这样顺手一划只会露出删除按钮，不会误删。已露出状态才允许继续左滑删除。
                if (!fromOpen) {
                    target = Math.min(target, revealWidth);
                }
                setOffset(target);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (dragging) {
                    if (offset >= deleteThreshold) {
                        commitDelete();
                    } else if (offset > revealWidth / 2f) {
                        animateTo(revealWidth);
                    } else {
                        animateTo(0);
                    }
                }
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            // cancel() 也会回调 onAnimationEnd；行已经离开窗口时不能再触发删除
            animator.removeAllListeners();
            animator.cancel();
        }
        if (openRow == this) openRow = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
