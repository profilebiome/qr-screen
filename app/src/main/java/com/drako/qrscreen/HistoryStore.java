package com.drako.qrscreen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Historial de lecturas guardado en SharedPreferences (sin base de datos ni librerías). */
final class HistoryStore {

    static final String SOURCE_SCREEN = "pantalla";
    static final String SOURCE_IMAGE = "imagen";

    static final class Item {
        final String text;
        final String format;
        final String source;
        final long time;

        Item(String text, String format, String source, long time) {
            this.text = text;
            this.format = format;
            this.source = source;
            this.time = time;
        }
    }

    private static final String PREFS = "qr_screen";
    private static final String KEY_HISTORY = "history_v1";
    private static final int MAX_ITEMS = 100;

    private HistoryStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void add(Context c, String text, String format, String source) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<Item> items = list(c);
        // Evita duplicar la misma lectura inmediatamente
        if (!items.isEmpty() && text.equals(items.get(0).text) && System.currentTimeMillis() - items.get(0).time < 8000) {
            return;
        }
        items.add(0, new Item(text, format, source, System.currentTimeMillis()));
        while (items.size() > MAX_ITEMS) {
            items.remove(items.size() - 1);
        }
        save(c, items);
    }

    static List<Item> list(Context c) {
        List<Item> items = new ArrayList<>();
        String raw = prefs(c).getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                items.add(new Item(o.optString("text"), o.optString("format", "QR_CODE"),
                        o.optString("source", SOURCE_SCREEN), o.optLong("time", 0)));
            }
        } catch (Throwable ignored) {
        }
        return items;
    }

    static void remove(Context c, long time) {
        List<Item> items = list(c);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).time == time) {
                items.remove(i);
            }
        }
        save(c, items);
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY_HISTORY).apply();
    }

    private static void save(Context c, List<Item> items) {
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("text", it.text);
                o.put("format", it.format);
                o.put("source", it.source);
                o.put("time", it.time);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        prefs(c).edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    static String relativeTime(long time) {
        if (time <= 0) {
            return "";
        }
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000) {
            return "ahora";
        }
        if (diff < 3_600_000) {
            return (diff / 60_000) + " min";
        }
        if (diff < 86_400_000) {
            return (diff / 3_600_000) + " h";
        }
        return new SimpleDateFormat("d MMM", new Locale("es")).format(new Date(time));
    }

    static String formatLabel(String format) {
        if (format == null) {
            return "Código";
        }
        switch (format) {
            case "QR_CODE":
                return "QR";
            case "DATA_MATRIX":
                return "Data Matrix";
            case "AZTEC":
                return "Aztec";
            case "PDF_417":
                return "PDF417";
            case "CODE_128":
                return "Code 128";
            case "CODE_39":
                return "Code 39";
            case "EAN_13":
                return "EAN-13";
            case "EAN_8":
                return "EAN-8";
            case "UPC_A":
                return "UPC-A";
            case "UPC_E":
                return "UPC-E";
            case "ITF":
                return "ITF";
            default:
                return format;
        }
    }
}
