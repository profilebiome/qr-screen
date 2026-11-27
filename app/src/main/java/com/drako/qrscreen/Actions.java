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
        if (t.isEmpty()) {
            return false;
        }
        String low = t.toLowerCase(Locale.ROOT);
        return low.startsWith("http://") || low.startsWith("https://") || low.startsWith("www.")
                || low.startsWith("mailto:") || low.startsWith("tel:") || low.startsWith("sms:")
                || low.startsWith("geo:") || low.startsWith("market://") || low.startsWith("otpauth://")
                || low.startsWith("wifi:") || low.startsWith("begin:vcard") || low.startsWith("bitcoin:")
                || low.startsWith("upi://") || low.startsWith("intent://")
                // dominios sin esquema: tienda.com, wa.me/521..., 192.168.1.1
                || looksLikeWeb(t);
    }

    /** true si el texto es un dominio o IP con pinta de URL, aunque no lleve https://. */
    static boolean looksLikeWeb(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t') {
                return false;
            }
        }
        String host = t;
        int cut = host.length();
        int slash = host.indexOf('/');
        if (slash >= 0) {
            cut = Math.min(cut, slash);
        }
        int query = host.indexOf('?');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        int hash = host.indexOf('#');
        if (hash >= 0) {
            cut = Math.min(cut, hash);
        }
        host = host.substring(0, cut);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (host.isEmpty()) {
            return false;
        }
        boolean ipv4 = true;
        int dots = 0;
        int digits = 0;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '.') {
                dots++;
                digits = 0;
            } else if (c >= '0' && c <= '9') {
                digits++;
                if (digits > 3) {
                    ipv4 = false;
                }
            } else {
                ipv4 = false;
                break;
            }
        }
        if (ipv4 && dots == 3) {
            return true;
        }
        int lastDot = host.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == host.length() - 1) {
            return false;
        }
        String tld = host.substring(lastDot + 1);
        if (tld.length() < 2) {
            return false;
        }
        for (int i = 0; i < tld.length(); i++) {
            char c = tld.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    static boolean open(Context c, String text) {
        if (!openable(text)) {
            return false;
        }
        String t = text.trim();
        String low = t.toLowerCase(Locale.ROOT);
        try {
            Intent i;
            if (!low.startsWith("http://") && !low.startsWith("https://") && looksLikeWeb(t)) {
                // Un enlace sin esquema se abre igual: se añade https://
                t = "https://" + t;
                low = t.toLowerCase(Locale.ROOT);
            }
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
