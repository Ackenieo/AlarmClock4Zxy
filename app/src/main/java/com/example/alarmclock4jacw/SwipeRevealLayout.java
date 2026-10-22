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
 * 左滑露出删除按钮的列表行。
 * 内容层始终铺满整行，删除按钮藏在右侧下方，向左滑动时内容层整体左移把它露出来。
 */
public class SwipeRevealLayout extends ViewGroup {

    /** 当前展开的行，保证同一时间只有一行是打开的。 */
    private static SwipeRevealLayout openRow;

    private View content;
    private View action;
    private View divider;

    private int actionWidth;
    private float offset;
    private float downX;
    private float downY;
    private float lastX;
    private boolean dragging;
    private final int touchSlop;
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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        content = findViewById(R.id.row_content);
        action = findViewById(R.id.delete_action);
        divider = getChildCount() > 2 ? getChildAt(2) : null;

        if (action != null) {
            action.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.run();
                }
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

    /** 收起当前展开的行（列表一开始滚动就调用）。 */
    public static void closeOpenRow() {
        if (openRow != null) {
            openRow.animateTo(0);
        }
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
            measureChild(action,
                    getChildMeasureSpec(widthMeasureSpec, 0, action.getLayoutParams().width),
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

        if (content != null) {
            content.layout(0, 0, width, height);
        }
        if (action != null) {
            actionWidth = action.getMeasuredWidth();
            action.layout(width - actionWidth, 0, width, height);
        }
        if (divider != null) {
            int dh = divider.getMeasuredHeight();
            divider.layout(0, height - dh, width, height);
        }
        applyOffset();
    }

    private void applyOffset() {
        if (content != null) {
            content.setTranslationX(-offset);
        }
    }

    private void setOffset(float value) {
        float max = actionWidth > 0 ? actionWidth : 0;
        offset = Math.max(0f, Math.min(max, value));
        applyOffset();
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                if (animator != null) {
                    animator.cancel();
                }
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
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - lastX;
                lastX = ev.getX();
                setOffset(offset - dx);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (dragging) {
                    animateTo(offset > actionWidth / 2f ? actionWidth : 0);
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
            animator.cancel();
        }
        if (openRow == this) {
            openRow = null;
        }
    }
}
