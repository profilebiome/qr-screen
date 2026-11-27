package com.drako.qrscreen;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Códigos QR creados por el usuario, con nombre, tipo y fecha. */
final class QrStore {

    static final class Entry {
        final long id;
        final String name;
        final String content;
        final String type;
        final long createdAt;

        Entry(long id, String name, String content, String type, long createdAt) {
            this.id = id;
            this.name = name;
            this.content = content;
            this.type = type;
            this.createdAt = createdAt;
        }
    }

    static final String TYPE_LINK = "link";
    static final String TYPE_TEXT = "text";
    static final String TYPE_WIFI = "wifi";
    static final String TYPE_CONTACT = "contact";
    static final String TYPE_EMAIL = "email";
    static final String TYPE_PHONE = "phone";
    static final String TYPE_SMS = "sms";
    static final String TYPE_GEO = "geo";
    static final String TYPE_PAY = "pay";
    static final String TYPE_OTP = "otp";

    private static final String KEY = "saved_v1";
    private static final int MAX_ITEMS = 200;

    private QrStore() {
    }

    static List<Entry> list(Context c) {
        List<Entry> items = new ArrayList<>();
        String raw = HistoryStore.prefs(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String content = o.optString("content", "");
                if (content.isEmpty()) {
                    continue;
                }
                items.add(new Entry(o.optLong("id", 0), o.optString("name", ""), content,
                        o.optString("type", TYPE_TEXT), o.optLong("createdAt", 0)));
            }
        } catch (Throwable ignored) {
        }
        return items;
    }

    static Entry add(Context c, String name, String content, String type) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        List<Entry> items = list(c);
        long id = System.currentTimeMillis();
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            cleanName = defaultName(content, type);
        }
        Entry entry = new Entry(id, cleanName, content.trim(), type, id);
        items.add(0, entry);
        save(c, items);
        return entry;
    }

    static void rename(Context c, long id, String name) {
        List<Entry> items = list(c);
        List<Entry> out = new ArrayList<>();
        for (Entry e : items) {
            if (e.id == id) {
                String clean = name == null ? "" : name.trim();
                out.add(new Entry(e.id, clean.isEmpty() ? defaultName(e.content, e.type) : clean,
                        e.content, e.type, e.createdAt));
            } else {
                out.add(e);
            }
        }
        save(c, out);
    }

    static void remove(Context c, long id) {
        List<Entry> items = list(c);
        List<Entry> out = new ArrayList<>();
        for (Entry e : items) {
            if (e.id != id) {
                out.add(e);
            }
        }
        save(c, out);
    }

    static void clear(Context c) {
        HistoryStore.prefs(c).edit().remove(KEY).apply();
    }

    private static void save(Context c, List<Entry> items) {
        JSONArray arr = new JSONArray();
        int count = 0;
        for (Entry e : items) {
            if (count++ >= MAX_ITEMS) {
                break;
            }
            try {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("content", e.content);
                o.put("type", e.type);
                o.put("createdAt", e.createdAt);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        HistoryStore.prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    static String detectType(String content) {
        if (content == null) {
            return TYPE_TEXT;
        }
        String t = content.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("wifi:")) {
            return TYPE_WIFI;
        }
        if (t.startsWith("begin:vcard") || t.startsWith("mecard:")) {
            return TYPE_CONTACT;
        }
        if (t.startsWith("otpauth://")) {
            return TYPE_OTP;
        }
        if (t.startsWith("mailto:")) {
            return TYPE_EMAIL;
        }
        if (t.startsWith("tel:") || t.startsWith("phone:")) {
            return TYPE_PHONE;
        }
        if (t.startsWith("sms:") || t.startsWith("smsto:")) {
            return TYPE_SMS;
        }
        if (t.startsWith("geo:")) {
            return TYPE_GEO;
        }
        if (t.startsWith("bitcoin:") || t.startsWith("upi:") || t.startsWith("ethereum:")) {
            return TYPE_PAY;
        }
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")
                || (t.contains(".") && !t.contains(" ") && t.length() > 4 && t.contains("/"))) {
            return TYPE_LINK;
        }
        return TYPE_TEXT;
    }

    static String typeLabel(String type) {
        if (type == null) {
            return "Texto";
        }
        switch (type) {
            case TYPE_LINK:
                return "Enlace";
            case TYPE_WIFI:
                return "Wi-Fi";
            case TYPE_CONTACT:
                return "Contacto";
            case TYPE_EMAIL:
                return "Correo";
            case TYPE_PHONE:
                return "Teléfono";
            case TYPE_SMS:
                return "SMS";
            case TYPE_GEO:
                return "Ubicación";
            case TYPE_PAY:
                return "Pago";
            case TYPE_OTP:
                return "2FA";
            default:
                return "Texto";
        }
    }

    static String defaultName(String content, String type) {
        if (content == null || content.trim().isEmpty()) {
            return "Sin título";
        }
        String label = typeLabel(type);
        if (TYPE_LINK.equals(type)) {
            String host = content.trim();
            try {
                if (!host.toLowerCase(Locale.ROOT).startsWith("http")) {
                    host = "https://" + host;
                }
                java.net.URI uri = java.net.URI.create(host);
                String h = uri.getHost();
                if (h != null && !h.isEmpty()) {
                    return h.startsWith("www.") ? h.substring(4) : h;
                }
            } catch (Throwable ignored) {
            }
        }
        String one = content.trim().replaceAll("\\s+", " ");
        if (one.length() > 32) {
            one = one.substring(0, 32) + "…";
        }
        return label + ": " + one;
    }

    static String when(long time) {
        return HistoryStore.relativeTime(time);
    }
}
