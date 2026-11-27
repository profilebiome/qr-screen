package com.drako.qrscreen;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Captura la pantalla con MediaProjection y entrega fotogramas a un listener.
 * No hay cámara en ningún punto del flujo.
 */
final class ScreenCapture implements ImageReader.OnImageAvailableListener {

    interface Listener {
        /** Se llama en el hilo de captura. La imagen se cierra al volver. */
        void onFrame(Image image, int width, int height);
    }

    private final Context appContext;
    private final MediaProjection projection;
    private final Listener listener;
    private final HandlerThread thread;
    private final Handler handler;

    private ImageReader reader;
    private VirtualDisplay display;
    private int width;
    private int height;
    private int densityDpi;

    private volatile boolean released;
    private MediaProjection.Callback projectionCallback;

    ScreenCapture(Context context, MediaProjection projection, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.projection = projection;
        this.listener = listener;
        this.thread = new HandlerThread("qr-screen-capture");
        this.thread.start();
        this.handler = new Handler(thread.getLooper());
    }

    void start() {
        int[] metrics = measureScreen(appContext);
        width = metrics[0];
        height = metrics[1];
        densityDpi = metrics[2];
        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                released = true;
            }
        };
        // Android 14 exige un callback registrado ANTES de crear el display virtual.
        projection.registerCallback(projectionCallback, handler);
        open();
    }

    /** Debe llamarse desde el hilo principal. Devuelve {ancho, alto, dpi}. */
    static int[] measureScreen(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int w = 0;
        int h = 0;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect b = wm.getMaximumWindowMetrics().getBounds();
                w = b.width();
                h = b.height();
            }
            if (w <= 0 || h <= 0) {
                Point p = new Point();
                wm.getDefaultDisplay().getRealSize(p);
                w = p.x;
                h = p.y;
            }
        } catch (Throwable ignored) {
        }
        if (w <= 0 || h <= 0) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            w = dm.widthPixels;
            h = dm.heightPixels;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int dpi = dm.densityDpi > 0 ? dm.densityDpi : DisplayMetrics.DENSITY_DEFAULT;
        return new int[]{w, h, dpi};
    }

    private void open() {
        try {
            reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this, handler);
            display = projection.createVirtualDisplay("qr-screen", width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
        } catch (Throwable t) {
            release();
        }
    }

    /** Llamar desde el hilo principal cuando cambia la rotación o el tamaño de pantalla. */
    void reconfigure() {
        if (released) {
            return;
        }
        int[] metrics = measureScreen(appContext);
        if (metrics[0] == width && metrics[1] == height) {
            return;
        }
        final int w = metrics[0];
        final int h = metrics[1];
        final int dpi = metrics[2];
        handler.post(() -> applySize(w, h, dpi));
    }

    private void applySize(int w, int h, int dpi) {
        if (released) {
            return;
        }
        try {
            ImageReader fresh = ImageReader.newInstance(w, h,
                    android.graphics.PixelFormat.RGBA_8888, 3);
            fresh.setOnImageAvailableListener(this, handler);
            if (display != null) {
                display.resize(w, h, dpi);
                display.setSurface(fresh.getSurface());
            }
            ImageReader previous = reader;
            reader = fresh;
            width = w;
            height = h;
            densityDpi = dpi;
            if (previous != null) {
                previous.setOnImageAvailableListener(null, null);
                previous.close();
            }
            if (display == null) {
                open();
            }
        } catch (Throwable t) {
            release();
        }
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    boolean isReleased() {
        return released;
    }

    @Override
    public void onImageAvailable(ImageReader r) {
        if (released) {
            return;
        }
        Image image;
        try {
            image = r.acquireLatestImage();
        } catch (Throwable t) {
            return;
        }
        if (image == null) {
            return;
        }
        try {
            if (listener != null) {
                listener.onFrame(image, image.getWidth(), image.getHeight());
            }
        } catch (Throwable ignored) {
            // un fotograma corrupto no debe tumbar el escaneo
        } finally {
            try {
                image.close();
            } catch (Throwable ignored) {
            }
        }
    }

    void release() {
        released = true;
        if (projectionCallback != null) {
            try {
                projection.unregisterCallback(projectionCallback);
            } catch (Throwable ignored) {
            }
            projectionCallback = null;
        }
        if (display != null) {
            try {
                display.setSurface(null);
            } catch (Throwable ignored) {
            }
            try {
                display.release();
            } catch (Throwable ignored) {
            }
            display = null;
        }
        if (reader != null) {
            try {
                reader.setOnImageAvailableListener(null, null);
            } catch (Throwable ignored) {
            }
            try {
                reader.close();
            } catch (Throwable ignored) {
            }
            reader = null;
        }
        try {
            thread.quitSafely();
        } catch (Throwable ignored) {
        }
    }

    /** Fotograma recortado en ARGB (0x00RRGGBB). */
    static final class Frame {
        final int[] pixels;
        final int width;
        final int height;

        Frame(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Copia una región del fotograma a un array ARGB (0x00RRGGBB) reduciendo la resolución
     * por {@code step}. La lectura es directa sobre el ByteBuffer, sin crear un Bitmap.
     */
    static Frame extract(Image image, Rect crop, int step) {
        if (image == null || crop == null) {
            return null;
        }
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }
        Image.Plane plane = planes[0];
        Rect r = new Rect(crop);
        if (!r.intersect(0, 0, image.getWidth(), image.getHeight()) || r.width() <= 2 || r.height() <= 2) {
            return null;
        }
        int s = Math.max(1, step);
        int outW = Math.max(1, r.width() / s);
        int outH = Math.max(1, r.height() / s);
        int[] out = new int[outW * outH];

        ByteBuffer buffer = plane.getBuffer();
        int start = buffer.position();
        int limit = buffer.limit();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int base = r.top * rowStride + r.left * pixelStride;
        try {
            int outIndex = 0;
            for (int y = 0; y < outH; y++) {
                int rowBase = start + base + y * s * rowStride;
                for (int x = 0; x < outW; x++) {
                    int offset = rowBase + x * s * pixelStride;
                    if (offset + 3 >= limit) {
                        return null;
                    }
                    int px = buffer.getInt(offset);
                    // RGBA_8888 en memoria: AABBGGRR -> lo pasamos a 0x00RRGGBB
                    out[outIndex++] = ((px & 0xFF) << 16) | (px & 0xFF00) | ((px >>> 16) & 0xFF);
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return new Frame(out, outW, outH);
    }

    static long now() {
        return SystemClock.elapsedRealtime();
    }
}
