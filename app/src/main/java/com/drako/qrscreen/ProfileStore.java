package com.drako.qrscreen;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Información personal y contactos para los que se crean códigos. */
final class ProfileStore {

    static final class Person {
        final String name;
        final String email;
        final String phone;

        Person(String name, String email, String phone) {
            this.name = name == null ? "" : name.trim();
            this.email = email == null ? "" : email.trim();
            this.phone = phone == null ? "" : phone.trim();
        }

        boolean isEmpty() {
            return name.isEmpty() && email.isEmpty() && phone.isEmpty();
        }

        String label() {
            if (!name.isEmpty()) {
                return name;
            }
            return email.isEmpty() ? phone : email;
        }
    }

    private static final String KEY_OWNER = "profile_owner";
    private static final String KEY_CONTACTS = "profile_contacts";
    private static final int MAX_CONTACTS = 100;

    private ProfileStore() {
    }

    static Person owner(Context c) {
        String raw = HistoryStore.prefs(c).getString(KEY_OWNER, null);
        if (raw == null) {
            return new Person("", "", "");
        }
        try {
            JSONObject o = new JSONObject(raw);
            return new Person(o.optString("name"), o.optString("email"), o.optString("phone"));
        } catch (Throwable t) {
            return new Person("", "", "");
        }
    }

    static void setOwner(Context c, String name, String email, String phone) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name == null ? "" : name.trim());
            o.put("email", email == null ? "" : email.trim());
            o.put("phone", phone == null ? "" : phone.trim());
            HistoryStore.prefs(c).edit().putString(KEY_OWNER, o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static List<Person> contacts(Context c) {
        List<Person> list = new ArrayList<>();
        String raw = HistoryStore.prefs(c).getString(KEY_CONTACTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    list.add(new Person(o.optString("name"), o.optString("email"), o.optString("phone")));
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    static boolean addContact(Context c, String name, String email, String phone) {
        Person person = new Person(name, email, phone);
        if (person.isEmpty()) {
            return false;
        }
        List<Person> list = contacts(c);
        if (list.size() >= MAX_CONTACTS) {
            return false;
        }
        list.add(person);
        save(c, list);
        return true;
    }

    static void removeContact(Context c, int index) {
        List<Person> list = contacts(c);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(c, list);
        }
    }

    private static void save(Context c, List<Person> list) {
        JSONArray arr = new JSONArray();
        for (Person p : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("email", p.email);
                o.put("phone", p.phone);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        HistoryStore.prefs(c).edit().putString(KEY_CONTACTS, arr.toString()).apply();
    }

    /** vCard 3.0 lista para usar como contenido de un QR. */
    static String vcard(Person p) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCARD\nVERSION:3.0\n");
        sb.append("FN:").append(p.name.isEmpty() ? p.label() : p.name).append('\n');
        if (!p.email.isEmpty()) {
            sb.append("EMAIL:").append(p.email).append('\n');
        }
        if (!p.phone.isEmpty()) {
            sb.append("TEL:").append(p.phone).append('\n');
        }
        sb.append("END:VCARD");
        return sb.toString();
    }

    static String details(Person p) {
        StringBuilder sb = new StringBuilder();
        if (!p.email.isEmpty()) {
            sb.append(p.email);
        }
        if (!p.phone.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(p.phone);
        }
        return sb.toString();
    }
}
