package com.drako.qrscreen;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.Locale;

/** Acciones sobre el texto leído: copiar, abrir, compartir, vibrar. */
final class Actions {

    private static ToneGenerator tone;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Actions() {
    }

    static void copy(Context c, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("QR", text));
                toast(c, "Copiado al portapapeles");
            }
        } catch (Throwable t) {
            toast(c, "No se pudo copiar");
        }
    }

    static void share(Context c, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            toast(c, "No se pudo compartir");
        }
    }

    /** Devuelve true si el texto se puede abrir con otra app. */
    static boolean openable(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        String low = t.toLowerCase(Locale.ROOT);
        return low.startsWith("http://") || low.startsWith("https://") || low.startsWith("www.")
                || low.startsWith("mailto:") || low.startsWith("tel:") || low.startsWith("sms:")
                || low.startsWith("geo:") || low.startsWith("market://") || low.startsWith("otpauth://")
                || low.startsWith("wifi:") || low.startsWith("begin:vcard") || low.startsWith("bitcoin:")
                || low.startsWith("upi://") || low.startsWith("intent://");
    }

    static boolean open(Context c, String text) {
        if (!openable(text)) {
            return false;
        }
        String t = text.trim();
        String low = t.toLowerCase(Locale.ROOT);
        try {
            Intent i;
            if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("market://")
                    || low.startsWith("geo:") || low.startsWith("otpauth://") || low.startsWith("upi://")
                    || low.startsWith("bitcoin:") || low.startsWith("intent://")) {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(t));
            } else if (low.startsWith("www.")) {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://" + t));
            } else if (low.startsWith("mailto:")) {
                i = new Intent(Intent.ACTION_SENDTO, Uri.parse(t));
            } else if (low.startsWith("tel:")) {
                i = new Intent(Intent.ACTION_DIAL, Uri.parse(t));
            } else if (low.startsWith("sms:")) {
                i = new Intent(Intent.ACTION_SENDTO, Uri.parse(t));
            } else if (low.startsWith("wifi:")) {
                // WIFI:S:...;T:...;P:...;;  -> copiar credenciales si no hay app
                copy(c, t);
                return true;
            } else if (low.startsWith("begin:vcard")) {
                copy(c, t);
                return true;
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(t));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable e) {
            copy(c, t);
            return true;
        }
    }

    static void toast(Context c, String message) {
        try {
            Toast.makeText(c.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    static void feedback(Context c) {
        vibrate(c);
        beep();
    }

    private static void vibrate(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(45);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void beep() {
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55);
            }
            ToneGenerator t = tone;
            t.startTone(ToneGenerator.TONE_PROP_ACK, 110);
            MAIN.postDelayed(() -> {
                try {
                    t.stopTone();
                } catch (Throwable ignored) {
                }
                try {
                    t.release();
                } catch (Throwable ignored) {
                }
                if (tone == t) {
                    tone = null;
                }
            }, 300);
        } catch (Throwable ignored) {
        }
    }
}
