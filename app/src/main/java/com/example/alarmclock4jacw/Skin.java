package com.example.alarmclock4jacw;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

/**
 * 一套皮肤 = 一个配色方案 + 一种特效流法。
 *
 * 预设皮肤的色值来自 res/values/colors.xml；色盘自定义皮肤由 SkinStore 现算，
 * 两者都对应 res/values/skins.xml 里的一个主题覆盖层（只改写 accent 等 4 个属性）。
 * 页面里凡是写 ?attr/accent 的地方，切换皮肤后都会跟着变。
 *
 * 配色决定「什么颜色」，{@link #fxStyle} 决定「光怎么流」——
 * 背景那一层由 {@link FlowLightView} 按这个值挑画法。
 */
public final class Skin {

    /** 存进 SharedPreferences 的 id：预设是 "mint" 这种，自定义是 "custom:12:1" */
    public final String id;
    /** 展示名资源。切换语言后按当前 Context 实时取，不能缓存成字符串 */
    @StringRes
    public final int nameRes;
    /** 主题覆盖层 */
    @StyleRes
    public final int styleRes;

    @ColorInt
    public final int accent;
    @ColorInt
    public final int deep;
    @ColorInt
    public final int soft;
    @ColorInt
    public final int on;

    /** 色盘格子：色相序号 0..47，明暗档 0..39；预设为 -1 */
    public final int hueIndex;
    public final int toneIndex;

    /** 流光流法，取 {@link FlowLightView} 里的 STYLE_* */
    public final int fxStyle;
    /** 流法的名字，比如「熔岩流光」，显示在皮肤卡片上 */
    @StringRes
    public final int fxNameRes;

    Skin(String id, @StringRes int nameRes, int styleRes,
         int accent, int deep, int soft, int on,
         int hueIndex, int toneIndex, int fxStyle, int fxNameRes) {
        this.id = id;
        this.nameRes = nameRes;
        this.styleRes = styleRes;
        this.accent = accent;
        this.deep = deep;
        this.soft = soft;
        this.on = on;
        this.hueIndex = hueIndex;
        this.toneIndex = toneIndex;
        this.fxStyle = fxStyle;
        this.fxNameRes = fxNameRes;
    }

    public boolean isCustom() {
        return hueIndex >= 0;
    }

    public String name(Context context) {
        return context.getString(nameRes);
    }
}
