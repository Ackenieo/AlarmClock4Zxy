"""生成 README 用的系统架构图（SVG）。

用脚本而不是手写 SVG：卡片按行自动排版、文字超宽自动缩字号，
以后增删模块只要改下面的 STAGES 数据。
"""
import os

W, H = 1240, 968
CHIP_X, CHIP_W, CHIP_H = 88, 36, 24
CARD_X, CARD_R = 148, 1200
CARD_H = 88
GAP = 14          # 同行卡片间距
STAGE_GAP = 36    # 阶段之间的连接段高度
CARD_GAP = 36     # 阶段标题到卡片
START_Y = 132

COLOR = {
    "ui": "#FF7A45",
    "sys": "#5B9DFF",
    "algo": "#35D0A5",
    "data": "#A78BFA",
}
KIND_NAME = {"ui": "界面", "sys": "系统", "algo": "算法", "data": "存储"}

STAGES = [
    dict(no="01", title="配置与持久化", note="用户设定闹钟", cards=[
        dict(t="MainActivity", s=["闹钟列表 · 启停开关", "权限引导 · 长按进调试"], k="ui"),
        dict(t="AlarmEditActivity", s=["时间 / 星期重复", "目标次数 5~60"], k="ui"),
        dict(t="AlarmStore", s=["SharedPreferences + JSON", "增删改查 · 下次响铃时间"], k="data"),
    ]),
    dict(no="02", title="精确定时", note="交给系统闹钟", cards=[
        dict(t="AlarmScheduler", s=["setAlarmClock 精确调度", "重复闹钟自动排下一次"], k="sys"),
        dict(t="AlarmManager", s=["系统服务 · Doze 豁免", "状态栏显示闹钟图标"], k="sys"),
        dict(t="BootReceiver", s=["开机 / 改时间 / 换时区", "重新登记所有闹钟"], k="sys"),
    ]),
    dict(no="03", title="响铃", note="声音只有服务能停", cards=[
        dict(t="AlarmReceiver", s=["BroadcastReceiver", "到点广播入口"], k="sys"),
        dict(t="AlarmService", s=["前台服务 mediaPlayback", "声音 + 振动 + 全屏通知"], k="sys"),
        dict(t="RingingActivity", s=["锁屏全屏 · 点亮屏幕", "唯一入口：开始挑战"], k="ui"),
    ]),
    dict(no="04", title="运动验证", note="相机 + 骨骼识别", cards=[
        dict(t="ExerciseActivity", s=["CameraX 前置预览", "ImageAnalysis 实时取流"], k="ui"),
        dict(t="ML Kit Pose", s=["33 关键点 · STREAM_MODE", "bundled 模型 · 离线可用"], k="algo"),
        dict(t="Landmarks", s=["统一归一化到 0~1", "像素坐标自动纠正"], k="algo"),
        dict(t="JumpingJackCounter", s=["滞回阈值 + 时间保持", "张开→并拢 计一次"], k="algo"),
        dict(t="PoseOverlay / Ring", s=["骨架叠加 · 进度环", "实时反馈"], k="ui"),
    ]),
    dict(no="05", title="闭环", note="达标才放行", cards=[
        dict(t="达到目标次数", s=["完成音 + 震动", "返回响铃页 RESULT_OK"], k="ui"),
        dict(t="AlarmService.stop()", s=["停声音 · 停振动", "移除通知 · 结束服务"], k="sys"),
        dict(t="未达标", s=["退回响铃页", "声音继续响"], k="ui"),
    ]),
]

LINKS = ["保存后 schedule()", "到点广播", "点击「开始挑战」", "达到目标次数"]


def text_w(s, fs):
    w = 0.0
    for ch in s:
        w += fs if ord(ch) > 0x2E80 else fs * 0.56
    return w


def fit(s, fs, limit, min_fs=9.0):
    """超宽就缩字号"""
    while fs > min_fs and text_w(s, fs) > limit:
        fs -= 0.5
    return fs


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def build():
    out = []
    a = out.append
    a(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
      f'viewBox="0 0 {W} {H}" font-family="Segoe UI, Microsoft YaHei, PingFang SC, sans-serif">')

    # 背景
    a('<defs>')
    a('<radialGradient id="bg" cx="18%" cy="0%" r="95%">'
      '<stop offset="0%" stop-color="#141A24"/>'
      '<stop offset="55%" stop-color="#0B0E13"/>'
      '<stop offset="100%" stop-color="#080A0E"/></radialGradient>')
    a('<filter id="glow" x="-40%" y="-40%" width="180%" height="180%">'
      '<feGaussianBlur stdDeviation="14" result="b"/>'
      '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>')
    a('</defs>')
    a(f'<rect width="{W}" height="{H}" fill="url(#bg)"/>')

    # 标题
    a(f'<circle cx="48" cy="56" r="7" fill="{COLOR["ui"]}" filter="url(#glow)"/>')
    a(f'<text x="66" y="63" font-size="23" font-weight="700" fill="#F2F4F8" letter-spacing="0.5">'
      f'开合跳闹钟 · 系统架构</text>')
    a(f'<text x="66" y="87" font-size="12" fill="#8B98AC">'
      f'从设定闹钟到运动达标关闭的完整链路　|　Java · CameraX · ML Kit Pose Detection</text>')

    # 图例
    lx = 760
    a(f'<text x="{lx}" y="52" font-size="10.5" fill="#5F6B7D" letter-spacing="1">图例</text>')
    for i, k in enumerate(["ui", "sys", "algo", "data"]):
        x = lx + i * 104
        a(f'<rect x="{x}" y="62" width="12" height="12" rx="3" fill="{COLOR[k]}"/>')
        a(f'<text x="{x + 19}" y="72" font-size="11" fill="#93A0B4">{KIND_NAME[k]}</text>')

    # 阶段
    y = START_Y
    positions = []
    for si, st in enumerate(STAGES):
        n = len(st["cards"])
        cw = (CARD_R - CARD_X - GAP * (n - 1)) / n
        # 阶段标题
        a(f'<rect x="{CHIP_X}" y="{y}" width="{CHIP_W}" height="{CHIP_H}" rx="8" '
          f'fill="{COLOR[st["cards"][0]["k"]]}" fill-opacity="0.16"/>')
        a(f'<text x="{CHIP_X + CHIP_W / 2}" y="{y + 16.5}" font-size="12" font-weight="700" '
          f'text-anchor="middle" fill="{COLOR[st["cards"][0]["k"]]}" '
          f'font-family="Consolas, monospace">{st["no"]}</text>')
        a(f'<text x="{CARD_X}" y="{y + 17}" font-size="14.5" font-weight="600" fill="#F2F4F8">'
          f'{esc(st["title"])}</text>')
        a(f'<text x="{CARD_X + text_w(st["title"], 14.5) + 12}" y="{y + 17}" font-size="11" '
          f'fill="#5F6B7D">{esc(st["note"])}</text>')

        # 卡片
        cy = y + CARD_GAP
        for ci, c in enumerate(st["cards"]):
            cx = CARD_X + ci * (cw + GAP)
            col = COLOR[c["k"]]
            a(f'<rect x="{cx}" y="{cy}" width="{cw:.1f}" height="{CARD_H}" rx="13" '
              f'fill="#171D26" stroke="#26303D" stroke-width="1"/>')
            a(f'<rect x="{cx}" y="{cy + 14}" width="3.5" height="{CARD_H - 28}" rx="1.75" fill="{col}"/>')
            avail = cw - 40
            fs_t = fit(c["t"], 13.5, avail)
            a(f'<text x="{cx + 16}" y="{cy + 31}" font-size="{fs_t:.1f}" font-weight="600" '
              f'fill="#F2F4F8">{esc(c["t"])}</text>')
            for li, line in enumerate(c["s"][:2]):
                fs_s = fit(line, 11, avail)
                a(f'<text x="{cx + 16}" y="{cy + 53 + li * 18}" font-size="{fs_s:.1f}" '
                  f'fill="#8B98AC">{esc(line)}</text>')
        positions.append((y, cy, cy + CARD_H))

        # 连接段
        if si < len(STAGES) - 1:
            y_next = cy + CARD_H + STAGE_GAP
            mid = cy + CARD_H + STAGE_GAP / 2
            x_line = CHIP_X + CHIP_W / 2
            a(f'<line x1="{x_line}" y1="{cy + CARD_H + 4}" x2="{x_line}" y2="{y_next - 10}" '
              f'stroke="#3A4553" stroke-width="1.6" stroke-dasharray="4 4"/>')
            a(f'<path d="M {x_line - 5},{y_next - 10} L {x_line + 5},{y_next - 10} L {x_line},{y_next - 1} Z" '
              f'fill="#3A4553"/>')
            label = LINKS[si]
            lw = text_w(label, 10.5) + 22
            a(f'<rect x="{x_line + 14}" y="{mid - 10}" width="{lw:.1f}" height="20" rx="10" '
              f'fill="#1B212B" stroke="#26303D"/>')
            a(f'<text x="{x_line + 25}" y="{mid + 4}" font-size="10.5" fill="#93A0B4">'
              f'{esc(label)}</text>')
            y = y_next
        else:
            y = cy + CARD_H

    # 回环：未达标 -> 03 响铃
    y5_mid = (positions[4][1] + positions[4][2]) / 2
    y3_head = positions[2][0] + 12
    x_loop = 40
    a(f'<path d="M {CARD_X},{y5_mid} H {x_loop} V {y3_head} H {CHIP_X - 8}" fill="none" '
      f'stroke="{COLOR["algo"]}" stroke-width="1.6" stroke-dasharray="5 4" '
      f'stroke-linejoin="round" opacity="0.85"/>')
    a(f'<path d="M {CHIP_X - 14},{y3_head - 5} L {CHIP_X - 14},{y3_head + 5} L {CHIP_X - 5},{y3_head} Z" '
      f'fill="{COLOR["algo"]}"/>')
    a(f'<text x="{CARD_X - 6}" y="{y5_mid - 12}" font-size="10.5" fill="{COLOR["algo"]}" '
      f'text-anchor="end">未达标 ↺ 回到 03</text>')

    # 底部说明
    fy = H - 26
    a(f'<line x1="40" y1="{fy - 26}" x2="{W - 40}" y2="{fy - 26}" stroke="#1E2632"/>')
    a(f'<text x="40" y="{fy - 6}" font-size="10.5" fill="#5F6B7D">'
      f'关键约束：声音 / 振动只由 AlarmService 控制，界面层无法单独关闭 —— 这是"必须运动"的前提。</text>')
    a(f'<text x="{W - 40}" y="{fy - 6}" font-size="10.5" fill="#5F6B7D" text-anchor="end">'
      f'docs/architecture.svg · tools/gen_architecture_svg.py 生成</text>')

    a('</svg>')
    return "\n".join(out)


if __name__ == "__main__":
    svg = build()
    path = os.path.join(os.path.dirname(__file__), "..", "docs", "architecture.svg")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(svg)
    print("written", os.path.abspath(path), len(svg), "bytes")
