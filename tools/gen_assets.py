"""Генератор текстур и звуков WorldBorder Event. Запуск: python3 tools/gen_assets.py (нужны numpy, Pillow, ffmpeg)."""
import numpy as np, os, subprocess, tempfile, wave
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'worldborderevent')
TEX = os.path.join(ROOT, 'textures', 'misc'); SND = os.path.join(ROOT, 'sounds')
os.makedirs(TEX, exist_ok=True); os.makedirs(SND, exist_ok=True)
rng = np.random.default_rng(7)

# ---------- стена: 128x256, бесшовная по обеим осям ----------
W, H = 128, 256
x = np.arange(W)[:, None] / W; y = np.arange(H)[None, :] / H   # [W,H]
tau = 2 * np.pi
base = 0.5 + 0.5 * np.sin(tau * (3 * x) + 0.6 * np.sin(tau * 2 * y))
base2 = 0.5 + 0.5 * np.sin(tau * (7 * x + 1 * y)) * np.sin(tau * 2 * y + 1)
stripes = np.zeros((W, H))
for cx, wid, amp in [(0.08, 0.012, 1.0), (0.27, 0.02, 0.8), (0.46, 0.010, 1.0), (0.63, 0.025, 0.7), (0.81, 0.012, 1.0), (0.93, 0.018, 0.6)]:
    wob = 0.012 * np.sin(tau * 3 * y + cx * 20) + 0.006 * np.sin(tau * 7 * y + cx * 9)
    d = np.abs(((x - cx + wob + 0.5) % 1.0) - 0.5)
    stripes += amp * np.exp(-(d / wid) ** 2) * (0.7 + 0.3 * np.sin(tau * 5 * y + cx * 30))
# тонкие электрические каналы (ломаные), периодичные по x и y
chan = np.zeros((W, H))
for k in range(5):
    cx = rng.random(); ph = rng.random() * 6
    path = cx + 0.03 * np.sin(tau * 4 * y + ph) + 0.015 * np.sin(tau * 11 * y + ph * 2)
    d = np.abs(((x - path + 0.5) % 1.0) - 0.5)
    chan += np.exp(-(d / 0.0035) ** 2) * (0.6 + 0.4 * np.sin(tau * 9 * y + ph))
# разряды: короткие яркие вспышки
spark = np.zeros((W, H))
for k in range(26):
    px, py = rng.integers(0, W), rng.integers(0, H)
    for s in range(rng.integers(3, 9)):
        spark[(px + s * rng.integers(-1, 2)) % W, (py + s) % H] = 1.0
noise = rng.random((W, H))
inten = np.clip(0.05 + 0.10 * base + 0.05 * base2 + 0.62 * stripes + 0.9 * chan + 0.9 * spark + 0.05 * noise, 0, 1.4)
dark = np.array([8, 30, 80]); teal = np.array([20, 190, 235]); white = np.array([215, 250, 255])
t = np.clip(inten, 0, 1)[..., None]
rgb = np.where(t < 0.6, dark + (teal - dark) * (t / 0.6), teal + (white - teal) * ((t - 0.6) / 0.4))
a = np.clip(0.38 + 0.62 * np.clip(inten, 0, 1), 0, 1)
img = np.dstack([rgb, a * 255]).astype(np.uint8).transpose(1, 0, 2)
Image.fromarray(img, 'RGBA').save(os.path.join(TEX, 'wall.png'))

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

# ---------- звуки ----------
SR = 44100
def save(name, sig, gain=0.8):
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * gain
    pcm = (sig * 32767).astype('<i2')
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: p = f.name
    with wave.open(p, 'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', p, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(SND, name + '.ogg')], check=True)
    os.unlink(p)
def tt(d): return np.arange(int(SR * d)) / SR
def lowpass(x, k): 
    ker = np.ones(k) / k; return np.convolve(np.r_[x[-k:], x, x[:k]], ker, 'same')[k:-k]

# гул: бесшовный цикл 8 с (все частоты кратны 1/8 Гц)
t = tt(8.0)
hum = np.sin(tau * 55 * t) + 0.6 * np.sin(tau * 82.5 * t + 1) + 0.35 * np.sin(tau * 110 * t + 2) + 0.2 * np.sin(tau * 165 * t)
hum *= 0.75 + 0.25 * np.sin(tau * 0.5 * t)
hum += 0.12 * lowpass(rng.standard_normal(len(t)), 60)
save('hum', hum, 0.7)
# низкий рокот: цикл 6 с
t = tt(6.0)
rum = np.sin(tau * 31 * t) + 0.8 * np.sin(tau * 41.5 * t + 0.7) + 0.5 * np.sin(tau * 23 * t + 2.1)
rum *= 0.7 + 0.3 * np.sin(tau * (1 / 3) * t) * np.sin(tau * (5 / 6) * t + 1)
rum += 0.35 * lowpass(rng.standard_normal(len(t)), 120)
save('rumble', rum, 0.9)
# нарастание
t = tt(4.0); env = (t / 4.0) ** 2.0
sw = (np.sin(tau * (40 + 60 * (t / 4)) * t) + 0.5 * np.sin(tau * (80 + 120 * (t / 4)) * t)) * env + 0.3 * lowpass(rng.standard_normal(len(t)), 30) * env
sw *= np.clip((4.0 - t) * 6, 0, 1)
save('swell', sw, 0.8)
# глубокий удар
t = tt(3.0); ph = tau * (34 * t - 8 * (1 - np.exp(-3 * t)) / 3 * 3)
boom = np.sin(ph) * np.exp(-1.4 * t) + 0.5 * np.sin(2 * ph + 1) * np.exp(-2.5 * t) + 0.8 * lowpass(rng.standard_normal(len(t)), 80) * np.exp(-3 * t)
boom *= np.clip(t * 400, 0, 1)
save('boom', boom, 0.95)
# попадания
t = tt(0.45); z = np.sin(tau * (900 * np.exp(-9 * t)) * t) * np.exp(-9 * t) + 0.25 * rng.standard_normal(len(t)) * np.exp(-30 * t) + 0.4 * np.sin(tau * 120 * t) * np.exp(-12 * t)
save('hit_light', z * np.clip(t * 600, 0, 1), 0.7)
t = tt(0.8); z = np.sin(tau * (500 * np.exp(-5 * t)) * t) * np.exp(-5 * t) + 0.5 * np.sin(tau * 70 * t) * np.exp(-6 * t) + 0.3 * rng.standard_normal(len(t)) * np.exp(-18 * t)
save('hit_heavy', z * np.clip(t * 600, 0, 1), 0.85)
print('ok')
