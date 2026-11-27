package com.drako.qrscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

/** Avisa de una versión nueva en GitHub, la descarga y abre el instalador de Android. */
final class Updater {

    private static final String KEY_LAST_CHECK = "update_last_check";
    private static final long CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Updater() {
    }

    static int currentVersionCode(android.content.Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Throwable t) {
            return BuildConfig.VERSION_CODE;
        }
    }

    static String currentVersionName(android.content.Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return BuildConfig.VERSION_NAME;
        }
    }

    /** Comprobación automática, como mucho cada 6 horas. */
    static void maybeCheck(Activity activity) {
        long last = HistoryStore.prefs(activity).getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            return;
        }
        check(activity, false);
    }

    static void check(Activity activity, boolean manual) {
        if (manual) {
            Actions.toast(activity, "Buscando actualizaciones…");
        }
        new Thread(() -> {
            UpdateChecker.Info info = null;
            String error = null;
            try {
                info = UpdateChecker.fetch();
            } catch (Throwable t) {
                error = t.getMessage() == null ? "Sin conexión" : t.getMessage();
            }
            final UpdateChecker.Info found = info;
            final String failure = error;
            MAIN.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (found == null) {
                    if (manual) {
                        Actions.toast(activity, "No se pudo comprobar: " + failure);
                    }
                    return;
                }
                HistoryStore.prefs(activity).edit()
                        .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                if (found.versionCode > currentVersionCode(activity)) {
                    showPrompt(activity, found);
                } else if (manual) {
                    Actions.toast(activity, "Ya tienes la última versión (" + found.versionName + ")");
                }
            });
        }, "qr-update-check").start();
    }

    private static void showPrompt(Activity activity, UpdateChecker.Info info) {
        String notes = info.notes == null || info.notes.isEmpty() ? "" : "\n\n" + info.notes;
        new AlertDialog.Builder(activity)
                .setTitle("Actualización disponible")
                .setMessage("Hay que actualizar: " + info.versionName + " ya está en GitHub."
                        + notes + "\n\n¿Descargar e instalar ahora?")
                .setPositiveButton("Actualizar", (d, w) -> begin(activity, info))
                .setNegativeButton("Ahora no", null)
                .show();
    }

    private static void begin(Activity activity, UpdateChecker.Info info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstall(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle("Permiso para instalar")
                    .setMessage("Android pide autorizar a QR Screen para instalar la actualización. "
                            + "Actívalo y vuelve a tocar Actualizar.")
                    .setPositiveButton("Abrir ajustes", (d, w) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                        } catch (Throwable t) {
                            Actions.toast(activity, "Abre Ajustes › Apps › Acceso especial");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        download(activity, info);
    }

    private static boolean canInstall(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    private static void download(Activity activity, UpdateChecker.Info info) {
        File dir = new File(activity.getCacheDir(), "update");
        File apk = new File(dir, "QR-Screen-" + safe(info.versionName) + ".apk");

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout box = Ui.column(activity);
        box.setPadding(Ui.px(activity, 20), Ui.px(activity, 18), Ui.px(activity, 20), Ui.px(activity, 16));
        TextView label = Ui.text(activity, "Descargando " + info.versionName + " · 0%", 13f, Ui.TEXT);
        box.addView(label, Ui.matchWrap());
        LinearLayout.LayoutParams barLp = Ui.matchWrap();
        barLp.topMargin = Ui.px(activity, 10);
        box.addView(bar, barLp);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box.setBackground(Ui.round(0xFF101827, Ui.dp(activity, 18), 0xFF26364F, Math.max(1, Ui.px(activity, 1))));
        dialog.setContentView(box);
        dialog.setCancelable(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);
        }
        dialog.show();

        new Thread(() -> {
            String error = null;
            String hash = null;
            try {
                hash = UpdateChecker.download(info.apkUrl, apk, percent ->
                        MAIN.post(() -> {
                            if (dialog.isShowing()) {
                                bar.setProgress(percent);
                                label.setText("Descargando " + info.versionName + " · " + percent + "%");
                            }
                        }));
            } catch (Throwable t) {
                error = t.getMessage() == null ? "Error de descarga" : t.getMessage();
            }
            final String failure = error;
            final String digest = hash;
            MAIN.post(() -> {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (digest == null) {
                    Actions.toast(activity, "No se pudo descargar: " + failure);
                    return;
                }
                if (!info.sha256.isEmpty() && !info.sha256.equalsIgnoreCase(digest)) {
                    apk.delete();
                    Actions.toast(activity, "El archivo descargado no coincide con la firma esperada");
                    return;
                }
                install(activity, apk);
            });
        }, "qr-update-download").start();
    }

    private static void install(Activity activity, File apk) {
        try {
            Uri uri = QrFileProvider.uriFor(activity, apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            Actions.toast(activity, "Confirma la instalación en el aviso de Android");
        } catch (Throwable t) {
            Actions.toast(activity, "No se pudo abrir el instalador");
        }
    }

    private static String safe(String name) {
        if (name == null || name.isEmpty()) {
            return "update";
        }
        return name.replaceAll("[^0-9A-Za-z._-]", "_");
    }
}
