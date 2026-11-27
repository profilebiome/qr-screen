package com.drako.qrscreen;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** Genera el bitmap de un código QR con ZXing (sin servicios externos). */
final class QrRenderer {

    private QrRenderer() {
    }

    /** Devuelve el QR en blanco y negro, o null si el contenido no cabe. */
    static Bitmap create(String content, int sizePx) {
        return create(content, sizePx, Color.BLACK, Color.WHITE);
    }

    static Bitmap create(String content, int sizePx, int dark, int light) {
        if (content == null || content.trim().isEmpty() || sizePx <= 0) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(content.trim(), BarcodeFormat.QR_CODE,
                    Math.max(64, sizePx), Math.max(64, sizePx), hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? dark : light;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }
}
