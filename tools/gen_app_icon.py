# -*- coding: utf-8 -*-
"""
开合跳闹钟 App 图标生成器

设计思路
  外环 = 衔尾蛇（时间在循环，闹钟一旦响起就不会自己停）
  内芯 = 金色 V 形指针（时钟指针，也是开合跳张开双腿的剪影）

与参考图的区别
  1. 蛇头从正上方挪到右上 1~2 点方向；蛇头是蛇身的自然收口，不是贴上去的一块
  2. 蛇身改为「朱红渐变 + 放射状鳞片弧」的规则排布，纹理连续，不是随机黑斑
  3. 内圈由白底改为近黑深腔 + 12 枚金色刻度，指针加顶点光珠
  4. 尾尖收成锥形探进蛇口，咬尾的闭环更明确

输出
  docs/icon/app-icon-dark.svg    深色版（推荐，贴合应用内纯黑 UI）
  docs/icon/app-icon-light.svg   浅色版（米白底，接近参考图气质）
"""
import math
import os

CX, CY = 512.0, 512.0
R_OUT = 392.0
R_IN = 246.0
R_MID = (R_OUT + R_IN) / 2
T = R_OUT - R_IN

# 角度约定：0=右，90=下（屏幕坐标），顺时针为正
HEAD_A0, HEAD_A1 = 256.0, 322.0     # 蛇头所占弧段，296 为吻端
TAIL_A0, TAIL_A1 = 297.0, 340.0     # 尾尖：286 收成尖点，314 恢复全粗
BODY_A0, BODY_A1 = 300.0, 626.0     # 蛇身：绕一圈，末端藏在蛇头下

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "icon")


def circle_path(cx, cy, r):
    return (f"M{cx - r:.2f},{cy:.2f} "
            f"a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0 "
            f"a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0 Z")


def annulus(cx, cy, r_in, r_out):
    return circle_path(cx, cy, r_out) + " " + circle_path(cx, cy, r_in)


def polar(r, deg):
    a = math.radians(deg)
    return CX + r * math.cos(a), CY + r * math.sin(a)


def _hash01(i):
    x = (i * 2654435761) % 4294967296
    return x / 4294967296.0


# ---------------------------------------------------------------- 带状几何

def band_samples(a0, a1, ro_fn, ri_fn, step=0.6):
    n = max(2, int(abs(a1 - a0) / step))
    out = []
    for i in range(n + 1):
        t = i / n
        a = a0 + (a1 - a0) * t
        out.append((a, ro_fn(t, a), ri_fn(t, a)))
    return out


def band_fill(samples, round_end=False):
    outer = [polar(ro, a) for a, ro, ri in samples]
    inner = [polar(ri, a) for a, ro, ri in samples]
    d = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in outer)
    if round_end:
        rr = math.dist(outer[-1], inner[-1]) / 2
        d += (f' A{rr:.2f},{rr:.2f} 0 0 1 {inner[-1][0]:.2f},{inner[-1][1]:.2f}')
        d += " L" + " L".join(f"{x:.2f},{y:.2f}" for x, y in list(reversed(inner))[1:])
    else:
        d += " L" + " L".join(f"{x:.2f},{y:.2f}" for x, y in reversed(inner))
    return d + " Z"


def band_edge(samples, which="outer"):
    pts = [polar(ro if which == "outer" else ri, a) for a, ro, ri in samples]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts)


def lerp_profile(pairs, t):
    for i in range(len(pairs) - 1):
        t0, v0 = pairs[i]
        t1, v1 = pairs[i + 1]
        if t0 <= t <= t1:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * k
    return pairs[-1][1]


# ---------------------------------------------------------------- 蛇身

HEAD_RO = [(0.0, 392), (0.20, 416), (0.50, 426), (0.80, 404), (1.0, 360)]
HEAD_RI = [(0.0, 246), (0.25, 242), (0.60, 240), (0.85, 243), (1.0, 246)]


def head_samples(t_end=1.0):
    a1 = HEAD_A0 + (HEAD_A1 - HEAD_A0) * t_end
    return band_samples(
        HEAD_A0, a1,
        lambda t, a: lerp_profile(HEAD_RO, t * t_end),
        lambda t, a: lerp_profile(HEAD_RI, t * t_end),
    )


def tail_samples():
    def thick(t):
        return (T / 2) * (0.62 + 0.38 * t)

    return band_samples(TAIL_A0, TAIL_A1,
                        lambda t, a: R_MID + thick(t),
                        lambda t, a: R_MID - thick(t))


def body_samples():
    return band_samples(BODY_A0, BODY_A1,
                        lambda t, a: R_OUT,
                        lambda t, a: R_IN)


# ---------------------------------------------------------------- 鳞片

def scale_band(r_in, r_out, step_r=23.0):
    """沿蛇身一圈圈排布的小鳞斑：位置和大小都带抖动，
    近看是鳞片，远看是参考图那种深浅交错的蛇皮花纹。"""
    dark, light = [], []
    row = 0
    r = r_in + step_r * 0.55
    k = 0
    while r < r_out - step_r * 0.35:
        circ = 2 * math.pi * r
        n = max(8, int(round(circ / 34.0)))
        offset = (row % 2) * (math.pi / n)
        for i in range(n):
            a = offset + 2 * math.pi * i / n
            j_a = (_hash01(k * 3 + 1) - 0.5) * (math.pi / n) * 0.55
            j_r = (_hash01(k * 7 + 2) - 0.5) * step_r * 0.42
            aa = a + j_a
            rr = r + j_r
            rx = 8.5 + _hash01(k * 11 + 3) * 7.5
            ry = 6.5 + _hash01(k * 13 + 5) * 5.5
            rot = math.degrees(aa) + 90
            cx, cy = polar(rr, math.degrees(aa))
            op = 0.34 + _hash01(k * 17 + 7) * 0.30
            dark.append(f'<ellipse cx="{cx:.2f}" cy="{cy:.2f}" rx="{rx:.2f}" ry="{ry:.2f}" '
                        f'transform="rotate({rot:.1f} {cx:.2f} {cy:.2f})" opacity="{op:.2f}"/>')
            light.append(f'<ellipse cx="{cx:.2f}" cy="{cy - ry * 0.55:.2f}" rx="{rx * 0.55:.2f}" '
                         f'ry="{ry * 0.3:.2f}" '
                         f'transform="rotate({rot:.1f} {cx:.2f} {cy - ry * 0.55:.2f})" '
                         f'opacity="{0.06 + _hash01(k * 23 + 9) * 0.10:.2f}"/>')
            k += 1
        row += 1
        r += step_r
    return "\n        ".join(dark), "\n        ".join(light)


# ---------------------------------------------------------------- 指针

def blade(x1, y1, x2, y2, w1, w2):
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy)
    ux, uy = dx / L, dy / L
    px, py = -uy, ux
    a1 = (x1 + px * w1 / 2, y1 + py * w1 / 2)
    a2 = (x1 - px * w1 / 2, y1 - py * w1 / 2)
    b1 = (x2 + px * w2 / 2, y2 + py * w2 / 2)
    b2 = (x2 - px * w2 / 2, y2 - py * w2 / 2)
    return (f'M{a1[0]:.2f},{a1[1]:.2f} L{b1[0]:.2f},{b1[1]:.2f} '
            f'A{w2 / 2:.2f},{w2 / 2:.2f} 0 0 1 {b2[0]:.2f},{b2[1]:.2f} '
            f'L{a2[0]:.2f},{a2[1]:.2f} '
            f'A{w1 / 2:.2f},{w1 / 2:.2f} 0 0 1 {a1[0]:.2f},{a1[1]:.2f} Z')


V_TIP_L = (438.0, 380.0)
V_TIP_R = (586.0, 380.0)
V_VERTEX = (512.0, 650.0)


def v_mark():
    left = blade(V_TIP_L[0], V_TIP_L[1], V_VERTEX[0] - 6, V_VERTEX[1] - 16, 27, 10)
    right = blade(V_TIP_R[0], V_TIP_R[1], V_VERTEX[0] + 6, V_VERTEX[1] - 16, 27, 10)
    return f'''
  <g filter="url(#goldGlow)">
    <path d="{left}" fill="url(#goldGrad)" stroke="#4A0408" stroke-width="3"/>
    <path d="{right}" fill="url(#goldGrad)" stroke="#4A0408" stroke-width="3"/>
    <circle cx="{V_VERTEX[0]}" cy="{V_VERTEX[1] - 6}" r="16" fill="#FFD97A"
            stroke="#4A0408" stroke-width="3.5"/>
    <circle cx="{V_VERTEX[0] - 5}" cy="{V_VERTEX[1] - 12}" r="5.5" fill="#FFFBEB"/>
  </g>'''


def ticks():
    out = []
    for i in range(12):
        a = -90 + i * 30
        major = i % 3 == 0
        ln = 26 if major else 14
        w = 7 if major else 4
        op = 0.5 if major else 0.24
        x1, y1 = polar(R_IN - 24, a)
        x2, y2 = polar(R_IN - 24 - ln, a)
        out.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
                   f'stroke="#FFC77A" stroke-width="{w}" stroke-linecap="round" opacity="{op}"/>')
    return "\n    ".join(out)


# ---------------------------------------------------------------- 组装

def build_svg(theme="dark", content_scale=1.0):
    if theme == "dark":
        bg_outer, bg_inner = "#050408", "#1B0A0E"
        void_c, void_edge = "#080406", "#150608"
        body = [("#E8454C", 0.0), ("#C4181F", 0.34), ("#8C0A14", 0.66), ("#4E0409", 1.0)]
        rim, ring_glow = "#FF9A6A", "#C41C26"
        scale_dark, scale_light = "#150002", "#FFB08C"
    else:
        bg_outer, bg_inner = "#F1E7D8", "#FFFDF9"
        void_c, void_edge = "#FFFCF6", "#F4E6D4"
        body = [("#F05A56", 0.0), ("#CC2028", 0.34), ("#940C16", 0.66), ("#56050B", 1.0)]
        rim, ring_glow = "#FFB08A", "#D8262F"
        scale_dark, scale_light = "#1A0104", "#FFC4A4"

    stops = "\n          ".join(f'<stop offset="{o:.2f}" stop-color="{c}"/>' for c, o in body)
    s_dark, s_light = scale_band(R_IN, R_OUT)

    head = head_samples()
    head_e = head_samples(0.84)
    bodyp = body_samples()

    head_fill = band_fill(head)
    body_fill = band_fill(bodyp)

    head_out = band_edge(head_e, "outer")
    head_in = band_edge(head_e, "inner")
    _a = polar(lerp_profile(HEAD_RO, 1.0), HEAD_A1)
    _b = polar(lerp_profile(HEAD_RI, 1.0), HEAD_A1)
    _rr = math.dist(_a, _b) / 2
    head_cap = (f"M{_a[0]:.2f},{_a[1]:.2f} A{_rr:.2f},{_rr:.2f} 0 0 1 "
                f"{_b[0]:.2f},{_b[1]:.2f}")

    jaw_pts = [polar(lerp_profile([(0.0, 300), (0.5, 258), (1.0, 252)], i / 12.0), 286 + 34 * i / 12.0)
               for i in range(13)]
    jaw = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in jaw_pts)
    eye_x, eye_y = polar(330, 292)
    brow_a = [polar(338, 278), polar(346, 284), polar(348, 294), polar(342, 302)]
    brow = "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in brow_a)

    clip_all = f"{body_fill} {head_fill}"
    wrap_open = (f'<g transform="translate({CX:.0f},{CY:.0f}) scale({content_scale}) '
                 f'translate({-CX:.0f},{-CY:.0f})">')

    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
  <defs>
    <radialGradient id="bgGrad" cx="50%" cy="44%" r="72%">
      <stop offset="0" stop-color="{bg_inner}"/>
      <stop offset="1" stop-color="{bg_outer}"/>
    </radialGradient>
    <radialGradient id="voidGrad" cx="50%" cy="44%" r="60%">
      <stop offset="0" stop-color="{void_c}"/>
      <stop offset="1" stop-color="{void_edge}"/>
    </radialGradient>
    <radialGradient id="bodyGrad" gradientUnits="userSpaceOnUse" cx="330" cy="250" r="560">
          {stops}
    </radialGradient>
    <linearGradient id="goldGrad" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#7A0C12"/>
      <stop offset="0.28" stop-color="#D8452A"/>
      <stop offset="0.60" stop-color="#FF9A34"/>
      <stop offset="1" stop-color="#FFDE8A"/>
    </linearGradient>
    <linearGradient id="ringShade" x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.10"/>
      <stop offset="0.45" stop-color="#000000" stop-opacity="0"/>
      <stop offset="1" stop-color="#000000" stop-opacity="0.22"/>
    </linearGradient>
    <filter id="goldGlow" x="-70%" y="-70%" width="240%" height="240%">
      <feGaussianBlur stdDeviation="12" result="b"/>
      <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="softShadow" x="-30%" y="-30%" width="160%" height="160%">
      <feDropShadow dx="0" dy="12" stdDeviation="20" flood-color="#000000" flood-opacity="0.5"/>
    </filter>
    <filter id="bigGlow" x="-50%" y="-50%" width="200%" height="200%">
      <feGaussianBlur stdDeviation="30"/>
    </filter>
    <clipPath id="bodyClip">
      <path d="{clip_all}"/>
    </clipPath>
  </defs>

  <rect width="1024" height="1024" fill="url(#bgGrad)"/>
  {wrap_open}
  <circle cx="{CX}" cy="{CY}" r="{R_OUT:.0f}" fill="none" stroke="{ring_glow}"
          stroke-width="18" opacity="0.26" filter="url(#bigGlow)"/>

  <circle cx="{CX}" cy="{CY}" r="{R_IN - 2:.0f}" fill="url(#voidGrad)"/>
  <g opacity="0.9">
    {ticks()}
  </g>

  <g filter="url(#softShadow)">
    <path d="{body_fill}" fill="url(#bodyGrad)" stroke="#160103" stroke-width="8"
          stroke-linejoin="round" stroke-linecap="round"/>
    <path d="{head_fill}" fill="url(#bodyGrad)"/>

    <g clip-path="url(#bodyClip)">
      <g fill="{scale_dark}">
        {s_dark}
      </g>
      <g fill="{scale_light}">
        {s_light}
      </g>
      <rect x="-300" y="-300" width="1624" height="1624" fill="url(#ringShade)"/>
    </g>

    <path d="{head_out}" fill="none" stroke="#160103" stroke-width="8"
          stroke-linecap="round" stroke-linejoin="round"/>
    <path d="{head_in}" fill="none" stroke="#160103" stroke-width="8"
          stroke-linecap="round" stroke-linejoin="round"/>

    <path d="{jaw}" fill="none" stroke="#160103" stroke-width="8"
          stroke-linecap="round" stroke-linejoin="round" opacity="0.85"/>
    <path d="{brow}" fill="none" stroke="#160103" stroke-width="7"
          stroke-linecap="round" stroke-linejoin="round" opacity="0.45"/>
    <circle cx="{eye_x:.2f}" cy="{eye_y:.2f}" r="11" fill="#FFC24A"
            stroke="#160103" stroke-width="4"/>
    <circle cx="{eye_x - 4:.2f}" cy="{eye_y - 4:.2f}" r="3.8" fill="#FFFBEB"/>
  </g>

  {v_mark()}
  </g>
</svg>
'''


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for theme in ("dark", "light"):
        svg = build_svg(theme)
        path = os.path.join(OUT_DIR, f"app-icon-{theme}.svg")
        with open(path, "w", encoding="utf-8") as f:
            f.write(svg)
        print("wrote", path, len(svg), "bytes")


if __name__ == "__main__":
    main()
