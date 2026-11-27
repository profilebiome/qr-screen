package com.drako.qrscreen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pequeños helpers de UI: no usamos AndroidX, todo se dibuja con APIs del sistema. */
final class Ui {

    static final int BG = 0xFF070A12;
    static final int BG_ELEV = 0xFF121826;
    static final int CARD = 0xFF0F1626;
    static final int STROKE = 0xFF1E2A3D;
    static final int ACCENT = 0xFF34D399;
    static final int ACCENT_DARK = 0xFF059669;
    static final int TEXT = 0xFFF4F7FB;
    static final int TEXT_DIM = 0xFF9AA6B8;
    static final int DANGER = 0xFFF87171;

    private Ui() {
    }

    static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    static int px(Context c, float v) {
        return Math.round(dp(c, v));
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable round(int color, float radiusPx, int strokeColor, float strokeWidthPx) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(Math.max(1, Math.round(strokeWidthPx)), strokeColor);
        return d;
    }

    static GradientDrawable gradient(int top, int bottom, float radiusPx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable strokeOnly(int color, float radiusPx, float widthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(1, Math.round(widthPx)), color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static Drawable ripple(Drawable content, int rippleColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
        }
        return content;
    }

    static TextView text(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView bold(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = text(c, s, sizeSp, color);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView button(Context c, CharSequence label, float sizeSp) {
        TextView t = bold(c, label, sizeSp, 0xFF07130F);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(px(c, 20), px(c, 14), px(c, 20), px(c, 14));
        t.setBackground(ripple(gradient(ACCENT, ACCENT_DARK, dp(c, 16)), 0x55000000));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    static TextView ghostButton(Context c, CharSequence label, float sizeSp, int textColor) {
        TextView t = bold(c, label, sizeSp, textColor);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(px(c, 18), px(c, 13), px(c, 18), px(c, 13));
        t.setBackground(ripple(round(0xFF14203A, dp(c, 16), 0xFF233350, Math.max(1, px(c, 1))), 0x33FFFFFF));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    static View dot(Context c, float sizeDp, int color) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(c, sizeDp), px(c, sizeDp));
        v.setLayoutParams(lp);
        return v;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(Context c, float wDp, float hDp) {
        return new LinearLayout.LayoutParams(px(c, wDp), px(c, hDp));
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    static int color(Context c, int resId) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? c.getColor(resId) : c.getResources().getColor(resId);
    }

    static void margin(View v, Context c, float left, float top, float right, float bottom) {
        android.view.ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.setMargins(px(c, left), px(c, top), px(c, right), px(c, bottom));
            v.setLayoutParams(lp);
        }
    }
}
