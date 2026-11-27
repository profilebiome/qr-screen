package com.drako.qrscreen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Ilustración animada de la tarjeta principal: un código sobre una pantalla. */
final class HeroArtView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private ValueAnimator animator;
    private float progress;
    private Shader glowShader;
    private Shader scanShader;

    // patrón fijo de módulos (estético, no es un QR real)
    private static final int[][] MODULES = {
            {7, 7}, {8, 7}, {10, 7}, {7, 8}, {9, 8}, {10, 8},
            {7, 9}, {8, 9}, {10, 9}, {7, 10}, {9, 10}, {10, 10}
    };

    HeroArtView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        line.setStyle(Paint.Style.FILL);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cx = w / 2f;
        glowShader = new RadialGradient(cx, h * 0.52f, Math.max(1f, h * 0.62f),
                new int[]{0x3334D399, 0x0034D399}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        float phoneH = h * 0.86f;
        float phoneW = Math.min(phoneH * 0.54f, w * 0.34f);
        float qrSide = phoneW * 0.66f;
        float left = cx - qrSide / 2f;
        float right = cx + qrSide / 2f;
        scanShader = new LinearGradient(left, 0f, right, 0f,
                new int[]{0x0034D399, 0xE634D399, 0xE634D399, 0x0034D399},
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;

        // resplandor de fondo
        if (glowShader != null) {
            glow.setShader(glowShader);
            canvas.drawRect(0, 0, w, h, glow);
        }

        float phoneH = h * 0.86f;
        float phoneW = Math.min(phoneH * 0.54f, w * 0.34f);
        float top = (h - phoneH) / 2f;
        float left = cx - phoneW / 2f;
        float radius = phoneW * 0.22f;

        // cuerpo del teléfono
        fill.setColor(0xFF0A1220);
        tmp.set(left, top, left + phoneW, top + phoneH);
        canvas.drawRoundRect(tmp, radius, radius, fill);
        stroke.setColor(0xFF25344C);
        stroke.setStrokeWidth(Ui.dp(getContext(), 1.4f));
        canvas.drawRoundRect(tmp, radius, radius, stroke);

        // pantalla
        float inset = phoneW * 0.07f;
        RectF screen = new RectF(left + inset, top + inset * 1.5f,
                left + phoneW - inset, top + phoneH - inset * 1.5f);
        fill.setColor(0xFF060C16);
        canvas.drawRoundRect(screen, radius * 0.7f, radius * 0.7f, fill);

        // muesca
        fill.setColor(0xFF16233A);
        tmp.set(cx - phoneW * 0.14f, top + phoneW * 0.055f,
                cx + phoneW * 0.14f, top + phoneW * 0.085f);
        canvas.drawRoundRect(tmp, phoneW * 0.02f, phoneW * 0.02f, fill);

        // QR
        float qrSide = phoneW * 0.66f;
        float qrLeft = cx - qrSide / 2f;
        float qrTop = screen.centerY() - qrSide * 0.46f;
        drawQr(canvas, qrLeft, qrTop, qrSide);

        // esquinas de escaneo
        stroke.setColor(Ui.ACCENT);
        stroke.setStrokeWidth(Ui.dp(getContext(), 2.2f));
        float arm = qrSide * 0.2f;
        float g = Ui.dp(getContext(), 5);
        float l = qrLeft - g;
        float r = qrLeft + qrSide + g;
        float t = qrTop - g;
        float b = qrTop + qrSide + g;
        canvas.drawLine(l, t, l + arm, t, stroke);
        canvas.drawLine(l, t, l, t + arm, stroke);
        canvas.drawLine(r, t, r - arm, t, stroke);
        canvas.drawLine(r, t, r, t + arm, stroke);
        canvas.drawLine(l, b, l + arm, b, stroke);
        canvas.drawLine(l, b, l, b - arm, stroke);
        canvas.drawLine(r, b, r - arm, b, stroke);
        canvas.drawLine(r, b, r, b - arm, stroke);

        // línea de escaneo
        float y = qrTop + qrSide * (0.08f + 0.84f * progress);
        if (scanShader != null) {
            line.setShader(scanShader);
        }
        float thickness = Math.max(2f, Ui.dp(getContext(), 1.8f));
        canvas.drawRect(l, y - thickness / 2f, r, y + thickness / 2f, line);
    }

    private void drawQr(Canvas canvas, float left, float top, float side) {
        float cell = side / 11f;
        fill.setColor(0xEE34D399);
        // tres patrones de posición
        drawFinder(canvas, left, top, cell, 0, 0);
        drawFinder(canvas, left, top, cell, 4, 0);
        drawFinder(canvas, left, top, cell, 0, 4);
        // módulos de datos
        for (int[] m : MODULES) {
            float x = left + m[0] * cell;
            float y = top + m[1] * cell;
            tmp.set(x, y, x + cell * 0.92f, y + cell * 0.92f);
            float rr = cell * 0.2f;
            canvas.drawRoundRect(tmp, rr, rr, fill);
        }
    }

    private void drawFinder(Canvas canvas, float left, float top, float cell, int gx, int gy) {
        float x = left + gx * cell;
        float y = top + gy * cell;
        tmp.set(x, y, x + cell * 7, y + cell * 7);
        stroke.setColor(0xEE34D399);
        stroke.setStrokeWidth(Math.max(2f, cell * 0.8f));
        canvas.drawRoundRect(tmp, cell * 1.2f, cell * 1.2f, stroke);
        tmp.set(x + cell * 2, y + cell * 2, x + cell * 5, y + cell * 5);
        fill.setColor(0xEE34D399);
        canvas.drawRoundRect(tmp, cell * 0.7f, cell * 0.7f, fill);
    }
}
