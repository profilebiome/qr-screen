package com.drako.qrscreen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Capas flotantes del escáner: píldora de estado, mira de escaneo, tarjeta de resultado
 * y modo de ajuste del área de captura.
 */
final class ScanOverlay {

    interface Callback {
        void onStopRequested();

        void onAdjustingChanged(boolean adjusting);

        void onRegionChanged();

        void onResultDismissed();
    }

    private static final String KEY_SIDE = "region_side";
    private static final String KEY_CX = "region_cx";
    private static final String KEY_CY = "region_cy";

    private final Context ctx;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());

    private View pillView;
    private WindowManager.LayoutParams pillParams;
    private FinderView finderView;
    private View finderRoot;
    private WindowManager.LayoutParams finderParams;
    private FrameLayout adjustRoot;
    private WindowManager.LayoutParams adjustParams;
    private View resultRoot;
    private WindowManager.LayoutParams resultParams;

    private boolean visible;
    private boolean adjusting;
    private boolean resultVisible;
    /** Medidas de pantalla cacheadas: regionPx() también se llama desde el hilo de captura. */
    private volatile Point cachedScreen;

    ScanOverlay(Context ctx, Callback callback) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = HistoryStore.prefs(ctx);
        this.callback = callback;
        this.cachedScreen = readScreen();
    }

    /** Llamar desde el hilo principal cuando cambia la rotación o el tamaño de pantalla. */
    void refreshScreenMetrics() {
        cachedScreen = readScreen();
    }

    // ---------------------------------------------------------------- medidas

    private Point readScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect b = wm.getMaximumWindowMetrics().getBounds();
                return new Point(b.width(), b.height());
            }
            Point p = new Point();
            wm.getDefaultDisplay().getRealSize(p);
            return p;
        } catch (Throwable t) {
            return new Point(ctx.getResources().getDisplayMetrics().widthPixels,
                    ctx.getResources().getDisplayMetrics().heightPixels);
        }
    }

    /** Área que se recorta de la pantalla, en píxeles reales. */
    Rect regionPx() {
        Point cached = cachedScreen;
        Point s = cached != null ? new Point(cached) : readScreen();
        float sideF = prefs.getFloat(KEY_SIDE, 0.62f);
        float cxF = prefs.getFloat(KEY_CX, 0.5f);
        float cyF = prefs.getFloat(KEY_CY, 0.5f);
        int min = Math.min(s.x, s.y);
        int side = Math.round(sideF * min);
        int minSide = Ui.px(ctx, 96);
        side = Math.max(minSide, Math.min(side, min));
        int cx = Math.round(cxF * s.x);
        int cy = Math.round(cyF * s.y);
        int left = clamp(cx - side / 2, 0, s.x - side);
        int top = clamp(cy - side / 2, 0, s.y - side);
        return new Rect(left, top, left + side, top + side);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Alto de la barra de navegación: las ventanas overlay quedan por debajo de ella. */
    private int navigationBarInset() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowMetrics metrics = wm.getCurrentWindowMetrics();
                return metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
            }
            int resId = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resId > 0) {
                return ctx.getResources().getDimensionPixelSize(resId);
            }
        } catch (Throwable ignored) {
        }
        return Ui.px(ctx, 48);
    }

    /** Alto de la barra de estado (la píldora se coloca justo debajo). */
    private int statusBarInset() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowMetrics metrics = wm.getCurrentWindowMetrics();
                return metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.statusBars()).top;
            }
            int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) {
                return ctx.getResources().getDimensionPixelSize(resId);
            }
        } catch (Throwable ignored) {
        }
        return Ui.px(ctx, 24);
    }

    private int type() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ---------------------------------------------------------------- mostrar

    void show() {
        if (visible) {
            return;
        }
        visible = true;
        main.post(() -> {
            addFinder();
            addPill();
        });
    }

    void hide() {
        visible = false;
        adjusting = false;
        resultVisible = false;
        main.post(() -> {
            hideResultInternal();
            removeAdjust();
            removeFinder();
            removePill();
        });
    }

    boolean isResultVisible() {
        return resultVisible;
    }

    /** true si al menos una capa está en pantalla (se comprueba desde el hilo principal). */
    boolean isShowing() {
        return visible && (finderRoot != null || pillView != null || resultRoot != null || adjustRoot != null);
    }

    // ---------------------------------------------------------------- píldora

    private void addPill() {
        if (!visible || resultVisible || adjusting || pillView != null) {
            return;
        }
        LinearLayout pill = Ui.row(ctx);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.px(ctx, 12), Ui.px(ctx, 6), Ui.px(ctx, 6), Ui.px(ctx, 6));
        pill.setBackground(Ui.round(0xF0121826, Ui.dp(ctx, 22), 0x33FFFFFF, Math.max(1, Ui.px(ctx, 1))));

        View dot = new View(ctx);
        dot.setBackground(circle(Ui.ACCENT));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.px(ctx, 8), Ui.px(ctx, 8));
        dotLp.setMarginEnd(Ui.px(ctx, 8));
        pill.addView(dot, dotLp);

        TextView label = Ui.bold(ctx, "Escaneando", 12f, 0xFFE7F5F0);
        label.setLetterSpacing(0.02f);
        pill.addView(label);

        TextView adjust = Ui.bold(ctx, "Ajustar", 11f, Ui.ACCENT);
        adjust.setPadding(Ui.px(ctx, 12), Ui.px(ctx, 7), Ui.px(ctx, 10), Ui.px(ctx, 7));
        adjust.setBackground(Ui.ripple(Ui.round(0x1A34D399, Ui.dp(ctx, 14)), 0x44FFFFFF));
        LinearLayout.LayoutParams adjustLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        adjustLp.setMarginStart(Ui.px(ctx, 8));
        adjust.setOnClickListener(v -> beginAdjust());
        pill.addView(adjust, adjustLp);

        TextView close = Ui.bold(ctx, "✕", 13f, 0xFFB9C4D4);
        close.setGravity(Gravity.CENTER);
        close.setWidth(Ui.px(ctx, 30));
        close.setHeight(Ui.px(ctx, 30));
        close.setBackground(Ui.ripple(Ui.round(0x00000000, Ui.dp(ctx, 15)), 0x44FFFFFF));
        close.setOnClickListener(v -> {
            if (callback != null) {
                callback.onStopRequested();
            }
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(Ui.px(ctx, 30), Ui.px(ctx, 30));
        closeLp.setMarginStart(Ui.px(ctx, 4));
        pill.addView(close, closeLp);

        WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = statusBarInset() + Ui.px(ctx, 10);
        lp.windowAnimations = android.R.style.Animation_Toast;
        try {
            wm.addView(pill, lp);
            pillView = pill;
            pillParams = lp;
        } catch (Throwable ignored) {
        }
    }

    private void removePill() {
        if (pillView == null) {
            return;
        }
        try {
            wm.removeViewImmediate(pillView);
        } catch (Throwable ignored) {
        }
        pillView = null;
        pillParams = null;
    }

    // ---------------------------------------------------------------- mira

    private void addFinder() {
        if (!visible || resultVisible || adjusting || finderRoot != null) {
            return;
        }
        FrameLayout root = new FrameLayout(ctx);
        FinderView v = new FinderView(ctx);
        root.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            wm.addView(root, lp);
            finderRoot = root;
            finderView = v;
            finderParams = lp;
        } catch (Throwable ignored) {
        }
    }

    private void removeFinder() {
        if (finderView != null) {
            finderView.stopAnimation();
        }
        if (finderRoot == null) {
            return;
        }
        try {
            wm.removeViewImmediate(finderRoot);
        } catch (Throwable ignored) {
        }
        finderRoot = null;
        finderView = null;
        finderParams = null;
    }

    private WindowManager.LayoutParams baseParams(int w, int h) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = type();
        lp.width = w;
        lp.height = h;
        lp.format = android.graphics.PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private final class FinderView extends View {
        private final Paint dimPaint = new Paint();
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();
        private ValueAnimator animator;
        private float progress;

        FinderView(Context c) {
            super(c);
            dimPaint.setColor(0x8C060A12);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setColor(Ui.ACCENT);
            glowPaint.setStrokeWidth(Ui.dp(ctx, 7));
            glowPaint.setAlpha(70);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Ui.ACCENT);
            strokePaint.setStrokeWidth(Ui.dp(ctx, 2.2f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            hintPaint.setColor(0xFFBFE9DA);
            hintPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f,
                    getResources().getDisplayMetrics()));
            hintPaint.setTextAlign(Paint.Align.CENTER);
            hintPaint.setTypeface(Typeface.DEFAULT_BOLD);

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1900);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                glowPaint.setAlpha((int) (50 + 90 * progress));
                invalidate();
            });
            animator.start();
        }

        void stopAnimation() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnimation();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Rect r = regionPx();
            int w = getWidth();
            int h = getHeight();

            // oscurece todo excepto el área de escaneo
            canvas.drawRect(0, 0, w, r.top, dimPaint);
            canvas.drawRect(0, r.bottom, w, h, dimPaint);
            canvas.drawRect(0, r.top, r.left, r.bottom, dimPaint);
            canvas.drawRect(r.right, r.top, w, r.bottom, dimPaint);

            rectF.set(r);
            canvas.drawRect(rectF, glowPaint);
            canvas.drawRect(rectF, strokePaint);

            float side = r.width();
            float arm = side * 0.14f;
            float inset = Ui.dp(ctx, 1);
            float left = r.left + inset;
            float top = r.top + inset;
            float right = r.right - inset;
            float bottom = r.bottom - inset;
            strokePaint.setStrokeWidth(Ui.dp(ctx, 3.4f));
            // esquinas
            canvas.drawLine(left, top, left + arm, top, strokePaint);
            canvas.drawLine(left, top, left, top + arm, strokePaint);
            canvas.drawLine(right, top, right - arm, top, strokePaint);
            canvas.drawLine(right, top, right, top + arm, strokePaint);
            canvas.drawLine(left, bottom, left + arm, bottom, strokePaint);
            canvas.drawLine(left, bottom, left, bottom - arm, strokePaint);
            canvas.drawLine(right, bottom, right - arm, bottom, strokePaint);
            canvas.drawLine(right, bottom, right, bottom - arm, strokePaint);

            String hint = "Apunta el código dentro del marco";
            float hintY = Math.min(h - Ui.px(ctx, 24), r.bottom + Ui.px(ctx, 30));
            if (hintY > r.bottom + Ui.px(ctx, 14)) {
                canvas.drawText(hint, r.centerX(), hintY, hintPaint);
            }
        }
    }

    // ---------------------------------------------------------------- ajuste

    private void beginAdjust() {
        if (adjusting) {
            return;
        }
        adjusting = true;
        main.post(() -> {
            removePill();
            removeFinder();
            if (callback != null) {
                callback.onAdjustingChanged(true);
            }
            FrameLayout root = new FrameLayout(ctx);
            AdjustView view = new AdjustView(ctx);
            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout bar = Ui.row(ctx);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.px(ctx, 16), Ui.px(ctx, 12), Ui.px(ctx, 16), Ui.px(ctx, 12));
            bar.setBackground(Ui.round(0xF0121826, Ui.dp(ctx, 20), 0x26FFFFFF, Math.max(1, Ui.px(ctx, 1))));

            TextView cancel = Ui.ghostButton(ctx, "Cancelar", 13f, Ui.TEXT);
            cancel.setOnClickListener(v -> endAdjust(false));

            TextView hint = Ui.text(ctx, "Arrastra para mover", 11.5f, Ui.TEXT_DIM);
            hint.setGravity(Gravity.CENTER);

            TextView done = Ui.button(ctx, "Listo", 13f);
            done.setOnClickListener(v -> endAdjust(true));

            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bar.addView(cancel, cancelLp);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            bar.addView(hint, hintLp);
            bar.addView(done, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            barLp.setMargins(Ui.px(ctx, 16), 0, Ui.px(ctx, 16), Ui.px(ctx, 28) + navigationBarInset());
            root.addView(bar, barLp);

            WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            try {
                wm.addView(root, lp);
                adjustRoot = root;
                adjustParams = lp;
            } catch (Throwable ignored) {
                adjusting = false;
            }
        });
    }

    private void endAdjust(boolean save) {
        if (save) {
            Rect r = currentAdjustRect();
            Point s = readScreen();
            int min = Math.min(s.x, s.y);
            prefs.edit()
                    .putFloat(KEY_SIDE, r.width() / (float) min)
                    .putFloat(KEY_CX, r.centerX() / (float) s.x)
                    .putFloat(KEY_CY, r.centerY() / (float) s.y)
                    .apply();
            if (callback != null) {
                callback.onRegionChanged();
            }
        }
        removeAdjust();
        adjusting = false;
        if (callback != null) {
            callback.onAdjustingChanged(false);
        }
        main.postDelayed(() -> {
            if (visible && !resultVisible) {
                addFinder();
                addPill();
            }
        }, 120);
    }

    private Rect currentAdjustRect() {
        if (adjustRoot == null || adjustRoot.getChildCount() == 0
                || !(adjustRoot.getChildAt(0) instanceof AdjustView)) {
            return regionPx();
        }
        return ((AdjustView) adjustRoot.getChildAt(0)).currentRect();
    }

    private void removeAdjust() {
        if (adjustRoot == null) {
            return;
        }
        try {
            wm.removeViewImmediate(adjustRoot);
        } catch (Throwable ignored) {
        }
        adjustRoot = null;
        adjustParams = null;
    }

    private final class AdjustView extends View {
        private final Paint dimPaint = new Paint();
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();
        private Rect rect;
        private float downX;
        private float downY;
        private int mode;
        private static final int MODE_NONE = 0;
        private static final int MODE_MOVE = 1;
        private static final int MODE_RESIZE = 2;

        AdjustView(Context c) {
            super(c);
            rect = regionPx();
            dimPaint.setColor(0xC7060A12);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Ui.ACCENT);
            strokePaint.setStrokeWidth(Ui.dp(ctx, 2.4f));
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setColor(0x5534D399);
            glowPaint.setStrokeWidth(Ui.dp(ctx, 8));
            handlePaint.setColor(Ui.ACCENT);
            hintPaint.setColor(0xFFBFE9DA);
            hintPaint.setTextAlign(Paint.Align.CENTER);
            hintPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f,
                    getResources().getDisplayMetrics()));
            hintPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        Rect currentRect() {
            return new Rect(rect);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (nearCorner(x, y)) {
                        mode = MODE_RESIZE;
                    } else if (rect.contains((int) x, (int) y)) {
                        mode = MODE_MOVE;
                    } else {
                        mode = MODE_NONE;
                    }
                    downX = x;
                    downY = y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - downX;
                    float dy = y - downY;
                    if (mode == MODE_MOVE) {
                        Point s = readScreen();
                        int left = clamp(rect.left + Math.round(dx), 0, s.x - rect.width());
                        int top = clamp(rect.top + Math.round(dy), 0, s.y - rect.height());
                        rect.offsetTo(left, top);
                    } else if (mode == MODE_RESIZE) {
                        Point s = readScreen();
                        int side = Math.round(Math.max(Math.max(dx, dy) + rect.width(), Ui.px(ctx, 96)));
                        side = Math.min(side, Math.min(s.x - rect.left, s.y - rect.top));
                        rect.right = rect.left + side;
                        rect.bottom = rect.top + side;
                    }
                    downX = x;
                    downY = y;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mode = MODE_NONE;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        private boolean nearCorner(float x, float y) {
            float threshold = Ui.px(ctx, 34);
            return Math.hypot(x - rect.right, y - rect.bottom) < threshold;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            canvas.drawRect(0, 0, w, rect.top, dimPaint);
            canvas.drawRect(0, rect.bottom, w, h, dimPaint);
            canvas.drawRect(0, rect.top, rect.left, rect.bottom, dimPaint);
            canvas.drawRect(rect.right, rect.top, w, rect.bottom, dimPaint);

            rectF.set(rect);
            canvas.drawRect(rectF, glowPaint);
            canvas.drawRect(rectF, strokePaint);

            canvas.drawCircle(rect.right, rect.bottom, Ui.dp(ctx, 13), handlePaint);
            handlePaint.setColor(0xFF0B1420);
            canvas.drawCircle(rect.right, rect.bottom, Ui.dp(ctx, 5.5f), handlePaint);
            handlePaint.setColor(Ui.ACCENT);

            canvas.drawText("Arrastra el marco · esquina para el tamaño",
                    rect.centerX(), Math.max(Ui.px(ctx, 40), rect.top - Ui.px(ctx, 16)), hintPaint);
        }
    }

    // ---------------------------------------------------------------- resultado

    void showResult(String text, String format) {
        resultVisible = true;
        main.post(() -> {
            removePill();
            removeFinder();
            buildResult(text, format);
        });
    }

    private void buildResult(String text, String format) {
        removeResultWindow();

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(0xD9040710);
        root.setOnClickListener(v -> dismissResult());

        LinearLayout card = Ui.column(ctx);
        card.setBackground(Ui.round(0xFF101827, Ui.dp(ctx, 26), 0xFF26364F, Math.max(1, Ui.px(ctx, 1))));
        card.setPadding(Ui.px(ctx, 18), Ui.px(ctx, 18), Ui.px(ctx, 18), Ui.px(ctx, 14));
        card.setClickable(true);
        card.setElevation(Ui.dp(ctx, 18));

        // cabecera
        LinearLayout header = Ui.row(ctx);
        TextView check = Ui.bold(ctx, "✓", 17f, 0xFF07130F);
        check.setGravity(Gravity.CENTER);
        check.setBackground(Ui.ripple(circle(Ui.ACCENT), 0x55000000));
        header.addView(check, new LinearLayout.LayoutParams(Ui.px(ctx, 38), Ui.px(ctx, 38)));

        LinearLayout titles = Ui.column(ctx);
        TextView title = Ui.bold(ctx, "Código detectado", 16f, Ui.TEXT);
        TextView sub = Ui.text(ctx, HistoryStore.formatLabel(format) + " · ya guardado en el historial",
                11.5f, Ui.TEXT_DIM);
        titles.addView(title);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.px(ctx, 3);
        titles.addView(sub, subLp);

        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginStart(Ui.px(ctx, 12));
        header.addView(titles, titlesLp);

        TextView close = Ui.bold(ctx, "✕", 15f, 0xFFB9C4D4);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(circle(0x1AFFFFFF), 0x44FFFFFF));
        close.setOnClickListener(v -> dismissResult());
        header.addView(close, new LinearLayout.LayoutParams(Ui.px(ctx, 34), Ui.px(ctx, 34)));

        card.addView(header, Ui.matchWrap());

        // contenido
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        TextView content = Ui.text(ctx, pretty(text), 15f, Ui.TEXT);
        content.setTextIsSelectable(true);
        content.setLineSpacing(Ui.dp(ctx, 3), 1f);
        content.setPadding(Ui.px(ctx, 14), Ui.px(ctx, 14), Ui.px(ctx, 14), Ui.px(ctx, 14));
        content.setBackground(Ui.round(0xFF0B1120, Ui.dp(ctx, 16), 0xFF1D2A42, Math.max(1, Ui.px(ctx, 1))));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int contentH = Math.min(Ui.px(ctx, 230), Math.round(screenH * 0.30f));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, contentH);
        scrollLp.topMargin = Ui.px(ctx, 14);
        card.addView(scroll, scrollLp);

        // botones
        LinearLayout buttons = Ui.row(ctx);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = Ui.px(ctx, 14);
        buttons.setLayoutParams(buttonsLp);

        // Si el contenido es un enlace (con o sin https://), abrir es la acción principal.
        boolean canOpen = Actions.openable(text);

        if (canOpen) {
            TextView open = Ui.button(ctx, "Abrir", 13.5f);
            open.setOnClickListener(v -> {
                Actions.open(ctx, text);
                dismissResult();
            });
            buttons.addView(open, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        TextView copy = canOpen
                ? Ui.ghostButton(ctx, "Copiar", 13.5f, Ui.TEXT)
                : Ui.button(ctx, "Copiar", 13.5f);
        copy.setOnClickListener(v -> Actions.copy(ctx, text));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (canOpen) {
            copyLp.setMarginStart(Ui.px(ctx, 8));
        }
        buttons.addView(copy, copyLp);

        TextView share = Ui.ghostButton(ctx, "Compartir", 13.5f, Ui.TEXT);
        share.setOnClickListener(v -> Actions.share(ctx, text));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        shareLp.setMarginStart(Ui.px(ctx, 8));
        buttons.addView(share, shareLp);

        card.addView(buttons);

        TextView again = Ui.bold(ctx, "Seguir escaneando", 12.5f, Ui.ACCENT);
        again.setGravity(Gravity.CENTER);
        again.setPadding(0, Ui.px(ctx, 14), 0, Ui.px(ctx, 6));
        again.setOnClickListener(v -> dismissResult());
        card.addView(again, Ui.matchWrap());

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardLp.setMargins(Ui.px(ctx, 14), 0, Ui.px(ctx, 14), Ui.px(ctx, 26) + navigationBarInset());
        root.addView(card, cardLp);

        WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        try {
            wm.addView(root, lp);
            resultRoot = root;
            resultParams = lp;
        } catch (Throwable ignored) {
            resultVisible = false;
        }

        card.setAlpha(0f);
        card.setTranslationY(Ui.dp(ctx, 26));
        card.animate().alpha(1f).translationY(0f).setDuration(190).start();
    }

    private String pretty(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n");
    }

    private void dismissResult() {
        if (!resultVisible) {
            return;
        }
        resultVisible = false;
        removeResultWindow();
        if (callback != null) {
            callback.onResultDismissed();
        }
        main.postDelayed(() -> {
            if (visible) {
                addFinder();
                addPill();
            }
        }, 150);
    }

    private void hideResultInternal() {
        resultVisible = false;
        removeResultWindow();
    }

    private void removeResultWindow() {
        if (resultRoot == null) {
            return;
        }
        try {
            wm.removeViewImmediate(resultRoot);
        } catch (Throwable ignored) {
        }
        resultRoot = null;
        resultParams = null;
    }

    private android.graphics.drawable.GradientDrawable circle(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
