"""生成番茄钟的提示音（22.05kHz / 16bit / 单声道 WAV）到 res/raw。

网页版用 WebAudio 合成同样的几种音色，这里的预设必须和 index.html 里的
TONES 列表一一对应（id 相同、性格相同）。

用法：python android/tools/gen_tones.py
"""

import math
import os
import struct
import wave

SR = 22050
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw"
)


def mk(dur):
    return [0.0] * int(dur * SR)


def env(t, attack, decay):
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) / decay)


def note(buf, start, dur, freq, decay, amp=0.5, harm=(1.0,), attack=0.004):
    """往缓冲区叠加一个带指数衰减的谐波音"""
    n0 = int(start * SR)
    n1 = min(int((start + dur) * SR), len(buf))
    for i in range(n0, n1):
        t = (i - n0) / SR
        e = env(t, attack, decay)
        s = 0.0
        for k, ha in enumerate(harm, start=1):
            s += ha * math.sin(2 * math.pi * freq * k * t)
        buf[i] += amp * e * s


def tone_chime():
    """清脆：三音上行（C5 E5 G5），即网页版的经典提示音"""
    b = mk(1.15)
    for i, f in enumerate((523.25, 659.25, 783.99)):
        note(b, i * 0.18, 0.7, f, 0.16, 0.5, (1.0, 0.25))
    return b


def tone_bell():
    """钟声：低音 + 非整数倍泛音，衰减长"""
    b = mk(2.2)
    for k, a in ((1.0, 1.0), (2.01, 0.5), (3.02, 0.22), (4.7, 0.1)):
        note(b, 0.0, 2.0, 392.0 * k, 0.75, 0.34 * a, (1.0,), 0.002)
    return b


def tone_marimba():
    """木琴：两音下行，敲击感"""
    b = mk(1.25)
    note(b, 0.0, 0.7, 587.33, 0.22, 0.5, (1.0, 0.2, 0.08))
    note(b, 0.22, 0.95, 880.0, 0.3, 0.45, (1.0, 0.2, 0.08))
    return b


def tone_wood():
    """木鱼：两声极短促的敲击"""
    b = mk(0.95)
    note(b, 0.0, 0.28, 1180.0, 0.045, 0.6, (1.0, 0.35, 0.15), 0.001)
    note(b, 0.34, 0.28, 1180.0, 0.045, 0.5, (1.0, 0.35, 0.15), 0.001)
    return b


def tone_beep():
    """电子：两声方波短音"""
    b = mk(0.95)
    harm = (1.0, 0.33, 0.2, 0.14)
    note(b, 0.0, 0.15, 880.0, 0.05, 0.45, harm, 0.002)
    note(b, 0.22, 0.15, 880.0, 0.05, 0.45, harm, 0.002)
    return b


def write(name, buf):
    peak = max(1e-9, max(abs(x) for x in buf))
    scale = 0.85 / peak
    frames = bytearray()
    for x in buf:
        v = int(max(-1.0, min(1.0, x * scale)) * 32767)
        frames += struct.pack("<h", v)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(bytes(frames))
    print("%-20s %6.1f KB  %.2fs" % (name, (len(frames) + 44) / 1024, len(buf) / SR))


if __name__ == "__main__":
    for fn, name in (
        (tone_chime, "tone_chime.wav"),
        (tone_bell, "tone_bell.wav"),
        (tone_marimba, "tone_marimba.wav"),
        (tone_wood, "tone_wood.wav"),
        (tone_beep, "tone_beep.wav"),
    ):
        write(name, fn())
