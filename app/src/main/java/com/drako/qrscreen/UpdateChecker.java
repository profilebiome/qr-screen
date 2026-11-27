package com.drako.qrscreen;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba y descarga actualizaciones desde GitHub Pages.
 * Java puro, sin dependencias de Android, para poder probarlo fuera del teléfono.
 */
final class UpdateChecker {

    static final String VERSION_URL = "https://profilebiome.github.io/qr-screen/version.json";

    static final class Info {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;
        final String notes;

        Info(int versionCode, String versionName, String apkUrl, String sha256, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.notes = notes;
        }
    }

    interface Progress {
        void onProgress(int percent);
    }

    private UpdateChecker() {
    }

    static Info fetch() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "QR-Screen-Updater");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            String json = readAll(connection.getInputStream());
            Info info = parse(json);
            if (info == null) {
                throw new IOException("version.json inválido");
            }
            return info;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Parser mínimo del version.json propio (sin dependencias). */
    static Info parse(String json) {
        if (json == null) {
            return null;
        }
        String versionCode = field(json, "versionCode");
        String versionName = field(json, "versionName");
        String apk = field(json, "apk");
        String sha = field(json, "sha256");
        String notes = field(json, "notes");
        if (versionCode == null || versionName == null || apk == null) {
            return null;
        }
        int code;
        try {
            code = Integer.parseInt(versionCode);
        } catch (NumberFormatException e) {
            return null;
        }
        return new Info(code, versionName, apk, sha == null ? "" : sha, notes == null ? "" : notes);
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\"([^\"]*)\"|(-?\\d+))")
                .matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2) : m.group(3);
    }

    /**
     * Descarga un APK y devuelve su SHA-256 en hexadecimal, o null si falla.
     */
    static String download(String url, File destination, Progress progress) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "QR-Screen-Updater");
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            int total = connection.getContentLength();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[16 * 1024];
                long done = 0;
                int read;
                int lastPercent = -1;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            if (progress != null) {
                                progress.onProgress(percent);
                            }
                        }
                    }
                }
            }
            return hex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 no disponible");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, read, "UTF-8"));
        }
        return sb.toString();
    }
}
