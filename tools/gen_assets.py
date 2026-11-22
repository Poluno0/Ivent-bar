"""Генератор текстур и звуков WorldBorder Event. Запуск: python3 tools/gen_assets.py (нужны numpy, Pillow, ffmpeg)."""
import numpy as np, os, subprocess, tempfile, wave
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'worldborderevent')
TEX = os.path.join(ROOT, 'textures', 'misc'); SND = os.path.join(ROOT, 'sounds')
os.makedirs(TEX, exist_ok=True); os.makedirs(SND, exist_ok=True)
rng = np.random.default_rng(7)
tau = 2 * np.pi

# ---------- кольцо ударной волны ----------
N = 128; yy, xx = np.mgrid[0:N, 0:N]; r = np.hypot(xx - N / 2 + .5, yy - N / 2 + .5) / (N / 2)
ring = np.exp(-((r - 0.82) / 0.07) ** 2) + 0.25 * np.exp(-((r - 0.6) / 0.12) ** 2) + 0.12 * np.clip(1 - r, 0, 1)
ring *= np.clip((1 - r) * 6, 0, 1)
a = np.clip(ring, 0, 1)
Image.fromarray(np.dstack([np.full((N, N), 150), np.full((N, N), 235), np.full((N, N), 255), a * 255]).astype(np.uint8), 'RGBA').save(os.path.join(TEX, 'ring.png'))

# ---------- вспышка / разряд ----------
N = 128; yy, xx = np.mgrid[0:N, 0:N]; dx = (xx - N / 2 + .5) / (N / 2); dy = (yy - N / 2 + .5) / (N / 2); r = np.hypot(dx, dy)
glow = np.exp(-(r / 0.28) ** 2)
ang = np.arctan2(dy, dx); rays = np.clip(np.cos(ang * 4 + 0.5 * np.sin(r * 9)) ** 8 + 0.6 * np.cos(ang * 7 + r * 5) ** 10, 0, 1) * np.exp(-(r / 0.7) ** 2)
fl = np.clip(glow + 0.8 * rays, 0, 1) * np.clip((1 - r) * 5, 0, 1)
Image.fromarray(np.dstack([np.full((N, N), 190), np.full((N, N), 245), np.full((N, N), 255), fl * 255]).astype(np.uint8), 'RGBA').save(os.path.join(TEX, 'flash.png'))

# ---------- полоса волны по периметру (по u — профиль, по v — однородно) ----------
Wb, Hb = 128, 8; u = np.linspace(0, 1, Wb)[:, None] * np.ones((1, Hb))
band = np.exp(-((u - 0.5) / 0.16) ** 2) * (0.85 + 0.15 * np.sin(np.linspace(0, tau * 3, Hb))[None, :])
band *= np.clip(np.minimum(u, 1 - u) * 12, 0, 1)
Image.fromarray(np.dstack([np.full((Wb, Hb), 170), np.full((Wb, Hb), 240), np.full((Wb, Hb), 255), band * 255]).astype(np.uint8).transpose(1, 0, 2), 'RGBA').save(os.path.join(TEX, 'band.png'))

# ---------- звуки (низкочастотные, «эпичные») ----------
SR = 44100
def save(name, sig, gain=0.97, drive=1.6):
    sig = np.tanh(drive * sig / (np.max(np.abs(sig)) + 1e-9))      # мягкое насыщение -> плотный, громкий звук
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * gain
    pcm = (sig * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: p = f.name
    with wave.open(p, 'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', p, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(SND, name + '.ogg')], check=True)
    os.unlink(p)
def tt(d): return np.arange(int(SR * d)) / SR
def lp(x, k):  # циклический НЧ-фильтр (для бесшовных петель)
    ker = np.ones(k) / k; return np.convolve(np.r_[x[-k:], x, x[:k]], ker, 'same')[k:-k]
def noise(n): return rng.standard_normal(n)
def reverb(x, d=2.5, k=300):
    n = int(SR * d); ir = lp(noise(n), 40) * np.exp(-np.arange(n) / (SR * d / 4.5))
    ir /= np.abs(ir).sum() + 1e-9; return np.convolve(x, ir * 8)[:len(x)]

# --- постоянный гул: глубокий космический дрон, бесшовный цикл 12 с (все частоты кратны 1/12 Гц)
t = tt(12.0)
hum = np.zeros_like(t)
for f, amp in [(32.0, 1.0), (32.25, 0.9), (48.0, 0.55), (48.5, 0.5), (64.0, 0.45), (96.0, 0.25), (144.0, 0.12)]:
    hum += amp * np.sin(tau * f * t + f)
hum *= 0.7 + 0.3 * np.sin(tau * (2 / 12) * t) * np.sin(tau * (3 / 12) * t + 1)
pad = np.sin(tau * 220 * t) * (0.5 + 0.5 * np.sin(tau * (1 / 12) * t)) + 0.8 * np.sin(tau * 330.0 * t + 1) * (0.5 + 0.5 * np.sin(tau * (2 / 12) * t + 2))
hum += 0.10 * pad + 0.35 * lp(noise(len(t)), 220)
save('hum', hum, 0.97, 1.3)

# --- рокот ивента: зловещий низ, цикл 8 с
t = tt(8.0)
rum = np.zeros_like(t)
for f, amp in [(22.0, 1.0), (29.0, 0.9), (36.5, 0.7), (44.0, 0.5), (58.0, 0.3)]:
    rum += amp * np.sin(tau * f * t + f * 0.3)
rum *= 0.75 + 0.25 * np.sin(tau * 6 * t) * (0.5 + 0.5 * np.sin(tau * (1 / 4) * t))   # «рычание»
rum += 0.6 * lp(noise(len(t)), 150) * (0.6 + 0.4 * np.sin(tau * 3 * t))
save('rumble', rum, 0.98, 2.2)

# --- пробуждение (12 с): что-то огромное набирает силу, высота и громкость растут
t = tt(12.0); u = t / 12.0
f = 22 + 30 * u ** 2.2
ph = tau * np.cumsum(f) / SR
aw = (np.sin(ph) + 0.6 * np.sin(2 * ph + 0.5) + 0.35 * np.sin(3 * ph + 1.0)) * (u ** 1.6)
aw += 0.9 * lp(noise(len(t)), 90) * (u ** 2.0)
aw += 0.5 * np.sin(tau * 5 * t) * np.sin(ph * 0.5) * u ** 2
aw *= np.clip((12.0 - t) * 8, 0, 1)
save('awaken', aw, 0.98, 2.4)

# --- гром (6 с): катящиеся низкие раскаты
t = tt(6.0)
env = np.zeros_like(t)
for st, amp, dec in [(0.0, 1.0, 1.2), (0.7, 0.7, 1.0), (1.6, 0.9, 1.4), (2.8, 0.5, 1.1)]:
    m = t >= st; env[m] += amp * np.exp(-(t[m] - st) / dec) * np.clip((t[m] - st) * 30, 0, 1)
th = (lp(noise(len(t)), 90) * 2.0 + lp(noise(len(t)), 400) * 0.8) * env + 0.6 * np.sin(tau * 38 * t) * env
th *= np.clip((6.0 - t) * 3, 0, 1)
save('thunder', th, 0.98, 2.0)

# --- удар по бочке: гулкий корпус (несколько затухающих резонансов) + глухой толчок + большая гулкая комната
def barrel(d, pitch=1.0, sub=0.6, tail=2.6):
    t = tt(d)
    modes = [(58, 1.0, 5.5), (91, 0.8, 7.0), (137, 0.55, 9.0), (188, 0.35, 12.0), (246, 0.2, 16.0)]
    x = np.zeros_like(t)
    for f, amp, dec in modes:
        x += amp * np.sin(tau * f * pitch * t + f) * np.exp(-dec * t * 0.55)
    x += sub * np.sin(tau * 36 * pitch * t) * np.exp(-3.0 * t)
    thump = lp(noise(len(t)), 40) * np.exp(-60 * t) * 1.2          # удар «по коже»
    x += thump + 0.35 * lp(noise(len(t)), 15) * np.exp(-140 * t)
    x *= np.clip(t * 1500, 0, 1)
    x += 0.6 * reverb(x, tail, 300)
    return x * np.clip((d - t) * 6, 0, 1)
save('drum', barrel(3.5, 1.0, 0.5, 2.6), 0.98, 1.8)
save('boom', barrel(6.0, 0.7, 1.2, 4.0) + 0.5 * lp(noise(int(SR * 6.0)), 70) * np.exp(-1.6 * tt(6.0)), 0.99, 2.0)

# --- удары по барьеру: бас
def bass_hit(d, f0, f1, dec, extra):
    t = tt(d); ph = tau * (f1 * t + (f0 - f1) * (1 - np.exp(-extra * t)) / extra)
    x = np.sin(ph) * np.exp(-dec * t) + 0.5 * np.sin(2 * ph) * np.exp(-dec * 1.8 * t)
    x += 0.5 * noise(len(t)) * np.exp(-90 * t) * 0.3
    return x * np.clip(t * 900, 0, 1) * np.clip((d - t) * 12, 0, 1)
save('hit_light', bass_hit(0.9, 120, 48, 5.0, 25), 0.98, 2.2)
save('hit_heavy', bass_hit(1.8, 95, 32, 2.6, 18) + 0.5 * lp(noise(int(SR * 1.8)), 70) * np.exp(-3 * tt(1.8)), 0.99, 2.6)

# pulse_a / pulse_b — файлы пользователя (из mp3, +10 дБ, лимитер), здесь не генерируются
print('ok')
