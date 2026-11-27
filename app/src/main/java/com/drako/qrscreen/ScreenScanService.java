package com.drako.qrscreen;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;

/**
 * Servicio en primer plano que captura la pantalla (MediaProjection) y decodifica
 * códigos en tiempo real. Nunca abre la cámara.
 */
public class ScreenScanService extends Service implements ScreenCapture.Listener {

    static final String ACTION_START = "com.drako.qrscreen.action.START";
    static final String ACTION_STOP = "com.drako.qrscreen.action.STOP";
    static final String EXTRA_RESULT_CODE = "extra_result_code";
    static final String EXTRA_RESULT_DATA = "extra_result_data";

    static final String ACTION_STATE = "com.drako.qrscreen.action.STATE";
    static final String EXTRA_STATE = "extra_state";
    static final String EXTRA_MESSAGE = "extra_message";
    static final String ACTION_RESULT = "com.drako.qrscreen.action.RESULT";
    static final String EXTRA_TEXT = "extra_text";
    static final String EXTRA_FORMAT = "extra_format";

    static final int STATE_IDLE = 0;
    static final int STATE_RUNNING = 1;
    static final int STATE_ERROR = 2;

    /** Solo válido dentro del mismo proceso. */
    static volatile boolean RUNNING;

    private static final String CHANNEL_ID = "scan";
    private static final int NOTIFICATION_ID = 1107;
    private static final long FRAME_INTERVAL_MS = 210;

    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private volatile ScreenCapture capture;
    private volatile ScanOverlay overlay;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;

    private volatile long lastAttempt;
    private int attemptCounter;
    private String lastText;
    private volatile long lastTextAt;
    private volatile boolean scanning;
    private boolean adjusting;
    private boolean resultShowing;
    private boolean stopping;

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopScanning();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startScanning(intent);
            return START_NOT_STICKY;
        }
        if (!RUNNING) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ arranque

    private void startScanning(Intent intent) {
        if (RUNNING) {
            return;
        }
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            reportError("No se autorizó la captura de pantalla.");
            stopSelf();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            reportError("Falta el permiso para mostrar el visor sobre otras apps.");
            stopSelf();
            return;
        }

        // El servicio debe estar en primer plano ANTES de pedir la proyección (Android 14+).
        startForegroundCompat(buildNotification(getString(R.string.notif_text)));

        try {
            projection = projectionManager.getMediaProjection(resultCode, resultData);
        } catch (Throwable t) {
            projection = null;
        }
        if (projection == null) {
            reportError("El sistema no permitió iniciar la captura.");
            stopScanning();
            return;
        }

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (!stopping) {
                    stopScanning();
                }
            }
        };
        projection.registerCallback(projectionCallback, main);

        overlay = new ScanOverlay(this, new ScanOverlay.Callback() {
            @Override
            public void onStopRequested() {
                Actions.toast(ScreenScanService.this, "Escáner detenido");
                stopScanning();
            }

            @Override
            public void onAdjustingChanged(boolean adjustingNow) {
                adjusting = adjustingNow;
                updateScanning();
            }

            @Override
            public void onRegionChanged() {
                lastAttempt = 0;
                attemptCounter = 0;
            }

            @Override
            public void onResultDismissed() {
                resultShowing = false;
                lastTextAt = SystemClock.elapsedRealtime();
                lastAttempt = 0;
                updateScanning();
            }
        });
        overlay.show();

        capture = new ScreenCapture(this, projection, this);
        capture.start();
        main.postDelayed(() -> {
            if (RUNNING && capture != null && capture.isReleased()) {
                reportError("No se pudo iniciar la captura de pantalla.");
                stopScanning();
                return;
            }
            ScanOverlay current = overlay;
            if (RUNNING && current != null && !current.isShowing()) {
                reportError("El sistema bloqueó el visor flotante. Revisa «Mostrar sobre otras apps».");
                stopScanning();
            }
        }, 1200);

        registerDisplayListener();
        scanning = true;
        RUNNING = true;
        attemptCounter = 0;
        lastAttempt = 0;
        resultShowing = false;
        adjusting = false;
        broadcastState(STATE_RUNNING, null);
    }

    private void updateScanning() {
        scanning = !adjusting && !resultShowing;
    }

    // ------------------------------------------------------------------ fotogramas

    @Override
    public void onFrame(Image image, int width, int height) {
        if (!scanning || capture == null || capture.isReleased()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastAttempt < FRAME_INTERVAL_MS) {
            return;
        }
        lastAttempt = now;
        attemptCounter++;

        ScanOverlay currentOverlay = overlay;
        Rect region = currentOverlay == null ? null : currentOverlay.regionPx();
        QrDecoder.Hit hit = null;

        // 1) zona elegida por el usuario, a resolución nativa
        if (region != null) {
            int side = Math.min(region.width(), region.height());
            int step = side > 1100 ? 2 : 1;
            ScreenCapture.Frame frame = ScreenCapture.extract(image, region, step);
            if (frame != null) {
                hit = QrDecoder.decode(frame.pixels, frame.width, frame.height, true);
            }
        }

        // 2) pantalla completa reducida: detecta el código en cualquier parte
        if (hit == null) {
            int[] size = scaledSize(width, height);
            ScreenCapture.Frame frame = ScreenCapture.extract(image,
                    new Rect(0, 0, width, height), size[2]);
            if (frame != null) {
                hit = QrDecoder.decode(frame.pixels, frame.width, frame.height, false);
            }
        }

        // 3) cada tanto, pantalla completa a resolución nativa (códigos muy pequeños)
        if (hit == null && attemptCounter % 6 == 0) {
            ScreenCapture.Frame frame = ScreenCapture.extract(image,
                    new Rect(0, 0, width, height), 1);
            if (frame != null) {
                hit = QrDecoder.decode(frame.pixels, frame.width, frame.height, false);
            }
        }

        if (hit != null && hit.text != null && !hit.text.isEmpty()) {
            onDetected(hit.text, hit.format);
        }
    }

    private int[] scaledSize(int width, int height) {
        int longest = Math.max(width, height);
        int step = longest > 2400 ? 3 : 2;
        return new int[]{width / step, height / step, step};
    }

    private void onDetected(final String text, final String format) {
        long now = SystemClock.elapsedRealtime();
        if (text.equals(lastText) && now - lastTextAt < 8000) {
            return;
        }
        lastText = text;
        lastTextAt = now;
        resultShowing = true;
        updateScanning();

        HistoryStore.add(this, text, format, HistoryStore.SOURCE_SCREEN);
        Actions.feedback(this);
        refreshNotification(text);

        main.post(() -> {
            if (overlay != null) {
                overlay.showResult(text, format);
            }
            Intent i = new Intent(ACTION_RESULT);
            i.setPackage(getPackageName());
            i.putExtra(EXTRA_TEXT, text);
            i.putExtra(EXTRA_FORMAT, format);
            sendBroadcast(i);
        });
    }

    // ------------------------------------------------------------------ parada

    private void stopScanning() {
        if (stopping) {
            return;
        }
        stopping = true;
        RUNNING = false;
        scanning = false;
        unregisterDisplayListener();

        if (capture != null) {
            capture.release();
            capture = null;
        }
        if (projectionCallback != null && projection != null) {
            try {
                projection.unregisterCallback(projectionCallback);
            } catch (Throwable ignored) {
            }
            projectionCallback = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Throwable ignored) {
            }
            projection = null;
        }
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        broadcastState(STATE_IDLE, null);
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        stopSelf();
        stopping = false;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        unregisterDisplayListener();
        if (capture != null) {
            capture.release();
            capture = null;
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Throwable ignored) {
            }
            projection = null;
        }
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        broadcastState(STATE_IDLE, null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ rotación

    private void registerDisplayListener() {
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            return;
        }
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    return;
                }
                final ScreenCapture current = capture;
                if (current != null) {
                    current.reconfigure();
                    lastAttempt = 0;
                    main.postDelayed(() -> {
                        if (RUNNING && current.isReleased()) {
                            reportError("Se perdió la captura al girar la pantalla. Vuelve a iniciar el escáner.");
                            stopScanning();
                        }
                    }, 700);
                }
                if (overlay != null) {
                    overlay.refreshScreenMetrics();
                }
            }
        };
        displayManager.registerDisplayListener(displayListener, main);
    }

    private void unregisterDisplayListener() {
        if (displayManager != null && displayListener != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (Throwable ignored) {
            }
        }
        displayListener = null;
        displayManager = null;
    }

    // ------------------------------------------------------------------ notificación

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_desc));
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 10,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                pendingFlags());
        PendingIntent stop = PendingIntent.getService(this, 11,
                new Intent(this, ScreenScanService.class).setAction(ACTION_STOP), pendingFlags());
        builder.setSmallIcon(R.drawable.ic_stat_scan)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_scan),
                        getString(R.string.notif_stop), stop).build());
        return builder.build();
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void refreshNotification(String text) {
        String snippet = text.replace('\n', ' ');
        if (snippet.length() > 60) {
            snippet = snippet.substring(0, 60) + "…";
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification("Leído: " + snippet));
            }
        } catch (Throwable ignored) {
        }
    }

    private void startForegroundCompat(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ avisos

    private void reportError(String message) {
        broadcastState(STATE_ERROR, message);
        Actions.toast(this, message);
    }

    private void broadcastState(int state, String message) {
        Intent i = new Intent(ACTION_STATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_STATE, state);
        if (message != null) {
            i.putExtra(EXTRA_MESSAGE, message);
        }
        sendBroadcast(i);
    }
}
