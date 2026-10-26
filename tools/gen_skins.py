#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 res/values/skins.xml —— 皮肤市场的全部主题覆盖层。

为什么是「预生成」而不是运行时算色？
    android 的 Theme.applyStyle(int resId, boolean) 只吃编译期的 style 资源，
    运行时拿一个任意 ARGB 是注不进主题的（?attr/accent 在 inflate 时就解析完了）。
    所以这里把色盘离散成 HUES 个色相 × TONE_STEPS 档明暗，每格一个 style，
    拖动时选最近的格子：色相误差 < 3.75°，明暗误差 < 半个档位。

输出：
    Skin.Obsidian / Skin.Mint / ... 6 套预设（手调色，引用 @color/skin_*）
    Skin_H00_T0 ... Skin_H47_T39    色盘格子（字面色值）
"""
import colorsys
import os

HUES = 48                 # 色相格数，步长 360/48 = 7.5°
TONE_STEPS = 40           # 字体色的明暗档位，0 = 最浅，39 = 最深（档距 1/40，肉眼分不出台阶）
BG_TONE_STEPS = 24        # 背景色的明暗档位（只在深色区间里走，档数不用那么多）
OUT = os.path.join(os.path.dirname(__file__), "..",
                   "app", "src", "main", "res", "values", "skins.xml")


def tone_sv(i):
    """明暗滑条第 i 档的 (饱和度, 明度)。与 SkinStore.toneSV() 必须完全一致。

    u=0   最浅：低饱和 + 满明度（粉彩色）
    u=0.5 标准：S .72 / V 1.0
    u=1   最深：高饱和 + 低明度（深色）
    档位加密到 40 档后，相邻两档的 RGB 差只有 2~4，滑起来看不出台阶。
    """
    u = i / (TONE_STEPS - 1)
    if u <= 0.5:
        return 0.42 + (0.72 - 0.42) * (u / 0.5), 1.0
    k = (u - 0.5) / 0.5
    return 0.72 + (0.88 - 0.72) * k, 1.0 - (1.0 - 0.62) * k


def bg_rgb(hue, i):
    """背景色第 i 档：只在深色区间里走，保证白字永远读得清。

    u=0 是能看出色相的深色（V .32），u=1 是纯黑；卡片色由它提亮派生。
    """
    u = i / (BG_TONE_STEPS - 1)
    return to_rgb(hue, 0.45 + 0.25 * u, 0.32 * (1.0 - u))


def lift(rgb, k):
    """把颜色朝白色提亮 k，用来派生 surface / surface_high / stroke。"""
    return tuple(round(v + (255 - v) * k) for v in rgb)


# 6 套手调预设：id 后缀, style 名, 4 个颜色资源
PRESETS = [
    ("Obsidian", "skin_orange"),
    ("Mint", "skin_mint"),
    ("Ocean", "skin_ocean"),
    ("Violet", "skin_violet"),
    ("Sakura", "skin_sakura"),
    ("Sunset", "skin_sunset"),
]


def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = (_lin(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def on_color(rgb):
    """主色上压什么颜色的字：黑/白里挑对比度更高的那个。"""
    l = luminance(rgb)
    return (16, 16, 16) if (l + 0.05) / 0.05 >= 1.05 / (l + 0.05) else (255, 255, 255)


def to_rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h / 360.0, s, v)
    return round(r * 255), round(g * 255), round(b * 255)


def hexc(rgb):
    return "#FF%02X%02X%02X" % rgb


def scale(rgb, k):
    return tuple(max(0, min(255, round(v * k))) for v in rgb)


def main():
    lines = []
    w = lines.append

    w('<?xml version="1.0" encoding="utf-8"?>')
    w('<!-- 本文件由 tools/gen_skins.py 生成，请勿手改。')
    w('     皮肤覆盖层只改写 4 个自定义颜色属性，且必须 parent=""，')
    w('     否则 AAPT 会按点号把 Skin 当成父样式去找，报 style/Skin not found。')
    w('     用法：Activity 里 SkinStore.applyTheme(this) 在 setContentView 之前调用。 -->')
    w('<resources>')
    w('')
    w('    <!-- ========== 6 套预设（手调色，见 colors.xml） ========== -->')

    for style, color in PRESETS:
        if style == "Obsidian":
            w('    <style name="Skin.%s" parent="" />' % style)
            continue
        w('    <style name="Skin.%s" parent="">' % style)
        w('        <item name="accent">@color/%s</item>' % color)
        w('        <item name="accentDeep">@color/%s_deep</item>' % color)
        w('        <item name="accentSoft">@color/%s_soft</item>' % color)
        w('        <item name="onAccent">@color/%s_on</item>' % color)
        w('    </style>')
        w('')

    w('    <!-- ========== 色盘格子：%d 色相 × %d 档明暗 ========== -->'
      % (HUES, TONE_STEPS))
    w('    <!-- 命名 Skin_Hxx_Ty，xx 为色相序号（0-%d），y 为明暗档（0 最浅 → %d 最深） -->'
      % (HUES - 1, TONE_STEPS - 1))

    for i in range(HUES):
        hue = i * 360.0 / HUES
        w('')
        for t in range(TONE_STEPS):
            s, v = tone_sv(t)
            accent = to_rgb(hue, s, v)
            deep = to_rgb(hue, min(1.0, s + 0.15), v * 0.68)
            soft = scale(accent, 0.14)
            on = on_color(accent)
            w('    <style name="Skin_H%02d_T%d" parent="">' % (i, t))
            w('        <item name="accent">%s</item>' % hexc(accent))
            w('        <item name="accentDeep">%s</item>' % hexc(deep))
            w('        <item name="accentSoft">%s</item>' % hexc(soft))
            w('        <item name="onAccent">%s</item>' % hexc(on))
            w('    </style>')

    w('')
    w('    <!-- ========== 背景色：%d 色相 × %d 档 ========== -->' % (HUES, BG_TONE_STEPS))
    w('    <!-- 命名 Bg_Hxx_Ty；卡片色由背景色朝白提亮 7.5%% / 13%% / 18%% 派生 -->')
    w('    <style name="Bg.Black" parent="" />')

    for i in range(HUES):
        hue = i * 360.0 / HUES
        w('')
        for t in range(BG_TONE_STEPS):
            bg = bg_rgb(hue, t)
            surface = lift(bg, 0.075)
            high = lift(bg, 0.13)
            stroke = lift(bg, 0.18)
            w('    <style name="Bg_H%02d_T%d" parent="">' % (i, t))
            w('        <item name="bgColor">%s</item>' % hexc(bg))
            w('        <item name="surfaceColor">%s</item>' % hexc(surface))
            w('        <item name="surfaceHighColor">%s</item>' % hexc(high))
            w('        <item name="strokeColor">%s</item>' % hexc(stroke))
            w('        <item name="android:colorBackground">%s</item>' % hexc(bg))
            w('        <item name="android:windowBackground">%s</item>' % hexc(bg))
            w('        <item name="android:statusBarColor">%s</item>' % hexc(bg))
            w('        <item name="android:navigationBarColor">%s</item>' % hexc(bg))
            w('    </style>')

    w('')
    w('</resources>')

    path = os.path.normpath(OUT)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote %s (%d styles)" % (path, len(PRESETS) + HUES * TONE_STEPS + 1 + HUES * BG_TONE_STEPS))


if __name__ == "__main__":
    main()
