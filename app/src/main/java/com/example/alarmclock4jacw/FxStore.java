package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/**
 * 特效 UI 的开关状态。
 *
 * 六项动效各自独立开关，默认全开；只存一个 bool，改完立刻落盘。
 * 页面在 onResume 里读一次就够了 —— 皮肤市场点一下自己 rebuild，
 * 回到首页/响铃页时按最新状态重新应用。
 *
 * 另外还有 {@link #PLAIN_BG}：它不是「多一个动效」而是「少一个背景」，
 * 在皮肤市场里挂在「特效皮肤」网格的最后一格，当成「不要流光」那一档来点。
 */
public final class FxStore {

    public static final String GLOW_BORDER = "glow_border";
    public static final String PARTICLE = "particle";
    public static final String SCANLINE = "scanline";
    public static final String NEON = "neon";
    public static final String GLITCH = "glitch";
    public static final String LIQUID = "liquid";

    /**
     * 无特效背景：开 = 整页流光关掉，只留纯色底。
     *
     * 注意它和其它六项**语义相反** —— 其它项「开」是多一个特效，它「开」是少一个特效。
     * 命名上让「开」始终等于「这张卡描述的状态成立」，UI 和代码就不用到处取反。
     */
    public static final String PLAIN_BG = "plain_bg";

    private static final String PREF = "fx_store";

    private FxStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context, String id) {
        // 只有「无特效背景」默认关（也就是默认有流光），其它默认开
        return prefs(context).getBoolean(id, !PLAIN_BG.equals(id));
    }

    public static void setEnabled(Context context, String id, boolean enabled) {
        prefs(context).edit().putBoolean(id, enabled).apply();
    }

    /** 取反并落盘，返回新状态。 */
    public static boolean toggle(Context context, String id) {
        boolean next = !isEnabled(context, id);
        setEnabled(context, id, next);
        return next;
    }

    /** 按开关把特效视图显示/藏起来。GONE 之后动效组件自己会停，不白烧帧。 */
    public static void applyVisibility(Context context, String id, View view) {
        if (view == null) return;
        view.setVisibility(isEnabled(context, id) ? View.VISIBLE : View.GONE);
    }

    /**
     * 流光背景是反向的：开了「无特效背景」就把它藏起来。
     * 藏成 GONE 之后 {@code FlowLightView} 不再排下一帧，静止不耗电。
     */
    public static void applyFlowBg(Context context, View flow) {
        if (flow == null) return;
        flow.setVisibility(isEnabled(context, PLAIN_BG) ? View.GONE : View.VISIBLE);
    }
}
