package com.example.worldborderevent;

/** Временная шкала ивента (тики, 20 тиков = 1 с). Общий код для сервера и клиента, без клиентских импортов. */
public final class EventTimeline {
    public static final long INTRO_TICKS = 240;      // 0–12 с: пробуждение, граница неподвижна
    public static final long MOVE_TICKS = 360;       // 12–30 с: отдаление
    public static final long FADE_TICKS = 100;       // затухание после движения
    public static final long TOTAL_TICKS = INTRO_TICKS + MOVE_TICKS + FADE_TICKS;
    public static final double RETREAT_BLOCKS = 1000.0; // Верхний мир; Незер = /8 = 125

    private EventTimeline() {}

    static float seg(float t, float a, float b, float va, float vb) {
        if (t <= a) return va;
        if (t >= b) return vb;
        return va + (vb - va) * (t - a) / (b - a);
    }

    /** Яркость свечения 0..1. t < 0 — ивента нет. */
    public static float glow(float t, boolean finished) {
        if (t < 0) return finished ? 0.0f : 0.45f;
        if (t < 60) return seg(t, 0, 60, 0.45f, 0.6f);
        if (t < 160) return seg(t, 60, 160, 0.6f, 0.85f);
        if (t < 240) return seg(t, 160, 240, 0.85f, 1.0f);
        if (t < 600) return seg(t, 240, 600, 1.0f, 0.7f);
        return seg(t, 600, 700, 0.7f, 0.0f);
    }

    /** Прозрачность стены. Когда отдаление пройдено на 80–100% пути (800–1000 блоков), стена тает до нуля и исчезает совсем. */
    public static float alpha(float t, boolean finished) {
        if (t < 0) return finished ? 0.0f : 1.0f;
        if (t < INTRO_TICKS) return 1.0f;
        if (t < INTRO_TICKS + MOVE_TICKS) {
            float p = (t - INTRO_TICKS) / (float) MOVE_TICKS;
            float e = p * p * (3f - 2f * p); // та же smoothstep, что и у сервера
            return Math.max(0f, Math.min(1f, 1f - (e - 0.8f) / 0.2f));
        }
        return 0.0f;
    }

    public static float shake(float t) {
        if (t < 0) return 0;
        if (t < 60) return seg(t, 0, 60, 0.05f, 0.15f);
        if (t < 160) return seg(t, 60, 160, 0.15f, 0.5f);
        if (t < 240) return seg(t, 160, 240, 0.5f, 1.0f);
        if (t < 600) return seg(t, 240, 600, 1.0f, 0.2f);
        return seg(t, 600, 700, 0.2f, 0f);
    }

    /** Громкость обычного гула у стены: резко приглушается при пробуждении. */
    public static float hum(float t, boolean finished) {
        if (t < 0) return finished ? 0.0f : 1.0f;
        if (t < 40) return seg(t, 0, 40, 1.0f, 0.05f);
        if (t < 480) return 0.05f;
        return seg(t, 480, 600, 0.05f, 0.0f); // гул уходит вместе со стеной
    }

    /** Громкость низкочастотного слоя. */
    public static float rumble(float t) {
        if (t < 0) return 0;
        if (t < 60) return seg(t, 0, 60, 0.1f, 0.4f);
        if (t < 160) return seg(t, 60, 160, 0.4f, 0.7f);
        if (t < 240) return seg(t, 160, 240, 0.7f, 1.0f);
        if (t < 600) return seg(t, 240, 600, 1.0f, 0.35f);
        return seg(t, 600, 700, 0.35f, 0f);
    }
}
