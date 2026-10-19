import math
import struct
import wave

SR = 44100


def tone(freq, dur, amp=0.6, attack=0.008, release=0.05, harmonics=((1, 1.0),)):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        v = 0.0
        for mult, weight in harmonics:
            v += weight * math.sin(2 * math.pi * freq * mult * t)
        # 包络
        env = 1.0
        if t < attack:
            env = t / attack
        elif t > dur - release:
            env = max(0.0, (dur - t) / release)
        # 轻微指数衰减，听起来更"电子"
        env *= math.exp(-1.6 * t)
        out.append(v * env * amp)
    return out


def silence(dur):
    return [0.0] * int(SR * dur)


def write_wav(path, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.92 / peak
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = int(max(-1.0, min(1.0, s * scale)) * 32767)
            frames += struct.pack("<h", v)
        w.writeframes(bytes(frames))


# 闹钟铃声：三短一停的"哔哔哔"，循环 6 秒
harm = ((1, 1.0), (2, 0.32), (3, 0.12))
beep = tone(988.0, 0.10, 0.7, harmonics=harm)
cycle = beep + silence(0.06) + beep + silence(0.06) + beep + silence(1.06)
alarm = cycle * 4
write_wav("app/src/main/res/raw/alarm_beep.wav", alarm)

# 完成音：C6 - E6 - G6 上行
success = (tone(1046.5, 0.11, 0.55, harmonics=harm)
           + silence(0.03)
           + tone(1318.5, 0.11, 0.55, harmonics=harm)
           + silence(0.03)
           + tone(1568.0, 0.45, 0.6, release=0.3, harmonics=harm))
write_wav("app/src/main/res/raw/success.wav", success)

print("ok")
