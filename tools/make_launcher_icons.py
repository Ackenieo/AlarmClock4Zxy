# -*- coding: utf-8 -*-
"""
把 docs/icon 里设计好的图标铺进 Android 工程：
  res/mipmap-*/ic_launcher.webp            传统图标（48dp）
  res/mipmap-*/ic_launcher_round.webp      圆形图标（预裁圆）
  res/mipmap-*/ic_launcher_foreground.webp 自适应图标前景（108dp）
  res/mipmap-anydpi/ic_launcher*.xml       自适应图标声明

渲染靠 Edge 无头模式跑 SVG，缩放用 LANCZOS，避免位图放大发虚。
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "app", "src", "main", "res")
ICON_DIR = os.path.join(ROOT, "docs", "icon")

sys.path.insert(0, HERE)
import gen_app_icon as gen  # noqa: E402

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

DENSITIES = [
    ("mdpi", 48, 108),
    ("hdpi", 72, 162),
    ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324),
    ("xxxhdpi", 192, 432),
]


def render(svg_text, size, out_png):
    """把 SVG 渲染成 size×size 的 PNG。"""
    fd, svg_path = tempfile.mkstemp(suffix=".svg")
    os.close(fd)
    with open(svg_path, "w", encoding="utf-8") as f:
        f.write(svg_text)
    subprocess.run(
        [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
         f"--window-size={size},{size}", f"--screenshot={out_png}",
         "file:///" + svg_path.replace("\\", "/")],
        check=True, capture_output=True,
    )
    os.unlink(svg_path)
    return Image.open(out_png).convert("RGB")


def round_mask(im):
    """给方形图加圆形 alpha 遮罩，做传统圆形图标。"""
    from PIL import ImageDraw
    w, h = im.size
    mask = Image.new("L", (w * 4, h * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, w * 4 - 1, h * 4 - 1), fill=255)
    mask = mask.resize((w, h), Image.LANCZOS)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def save_webp(im, path, lossless=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if lossless:
        im.save(path, "WEBP", lossless=True, method=6)
    else:
        im.save(path, "WEBP", quality=95, method=6)


def main():
    # 传统图标：留一点边距，避免部分启动器把图裁掉
    legacy_svg = gen.build_svg("dark", content_scale=0.94)
    legacy_master = render(legacy_svg, 1024, os.path.join(ICON_DIR, "_master_legacy.png"))

    # 自适应前景：内容收进 66% 安全区
    fg_svg = gen.build_svg("dark", content_scale=0.86)
    fg_master = render(fg_svg, 1024, os.path.join(ICON_DIR, "_master_fg.png"))

    for dens, legacy_px, fg_px in DENSITIES:
        d = os.path.join(RES, f"mipmap-{dens}")

        sq = legacy_master.resize((legacy_px, legacy_px), Image.LANCZOS)
        save_webp(sq, os.path.join(d, "ic_launcher.webp"))

        rnd = round_mask(sq)
        save_webp(rnd, os.path.join(d, "ic_launcher_round.webp"), lossless=True)

        fg = fg_master.resize((fg_px, fg_px), Image.LANCZOS)
        save_webp(fg, os.path.join(d, "ic_launcher_foreground.webp"))
        print(f"{dens}: legacy {legacy_px}px, foreground {fg_px}px")

    # 自适应图标声明
    adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        p = os.path.join(RES, "mipmap-anydpi", name)
        with open(p, "w", encoding="utf-8") as f:
            f.write(adaptive)
        print("wrote", p)

    # 背景色
    cp = os.path.join(RES, "values", "colors.xml")
    txt = open(cp, encoding="utf-8").read()
    if "ic_launcher_background" not in txt:
        txt = txt.replace("</resources>",
                          '    <color name="ic_launcher_background">#FF080509</color>\n</resources>')
        open(cp, "w", encoding="utf-8").write(txt)
        print("added ic_launcher_background color")

    # 旧的机器人矢量图不再使用
    for dead in ("ic_launcher_background.xml", "ic_launcher_foreground.xml"):
        p = os.path.join(RES, "drawable", dead)
        if os.path.exists(p):
            os.remove(p)
            print("removed", p)


if __name__ == "__main__":
    main()
