package com.drako.qrscreen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.InvertedLuminanceSource;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Decodificación de códigos usando ZXing (puro Java, sin cámara). */
final class QrDecoder {

    static final class Hit {
        final String text;
        final String format;
        final float[] corners;

        Hit(String text, String format, float[] corners) {
            this.text = text;
            this.format = format;
            this.corners = corners;
        }
    }

    private QrDecoder() {
    }

    /**
     * @param argb          pixels 0x00RRGGBB
     * @param tryAllFormats además de QR, intenta códigos de barras 1D
     */
    static Hit decode(int[] argb, int width, int height, boolean tryAllFormats) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) {
            return null;
        }
        LuminanceSource src = new RGBLuminanceSource(width, height, argb);
        Hit hit = attempt(src, tryAllFormats);
        if (hit == null) {
            hit = attempt(new InvertedLuminanceSource(src), tryAllFormats);
        }
        return hit;
    }

    private static Hit attempt(LuminanceSource source, boolean tryAllFormats) {
        byte[] data;
        try {
            data = source.getMatrix();
        } catch (Throwable t) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();

        // 1) QR con detección agresiva
        Hit hit = decodeWith(new QRCodeReader(), qrHints(), new BinaryBitmap(new HybridBinarizer(
                new MatrixSource(data, w, h))));
        if (hit != null) {
            return hit;
        }

        // 2) mismo QR con binarizador global (mejor para fondos muy uniformes)
        hit = decodeWith(new QRCodeReader(), qrHints(), new BinaryBitmap(new GlobalHistogramBinarizer(
                new MatrixSource(data, w, h))));
        if (hit != null) {
            return hit;
        }

        // 3) el resto de formatos (1D y 2D): solo como respaldo
        if (tryAllFormats) {
            MultiFormatReader multi = new MultiFormatReader();
            multi.setHints(allHints());
            try {
                Result r = multi.decode(new BinaryBitmap(new HybridBinarizer(new MatrixSource(data, w, h))));
                return toHit(r);
            } catch (ReaderException ignored) {
                return null;
            } finally {
                multi.reset();
            }
        }
        return null;
    }

    private static Hit decodeWith(QRCodeReader reader, Map<DecodeHintType, ?> hints, BinaryBitmap bitmap) {
        try {
            return toHit(reader.decode(bitmap, hints));
        } catch (ReaderException ignored) {
            return null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private static Hit toHit(Result r) {
        if (r == null || r.getText() == null) {
            return null;
        }
        float[] corners = null;
        ResultPoint[] pts = r.getResultPoints();
        if (pts != null && pts.length > 0) {
            corners = new float[pts.length * 2];
            for (int i = 0; i < pts.length; i++) {
                corners[i * 2] = pts[i].getX();
                corners[i * 2 + 1] = pts[i].getY();
            }
        }
        return new Hit(r.getText(), r.getBarcodeFormat() == null ? "QR_CODE" : r.getBarcodeFormat().name(), corners);
    }

    private static Map<DecodeHintType, Object> qrHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        return hints;
    }

    private static Map<DecodeHintType, Object> allHints() {
        Map<DecodeHintType, Object> hints = qrHints();
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        formats.add(BarcodeFormat.DATA_MATRIX);
        formats.add(BarcodeFormat.AZTEC);
        formats.add(BarcodeFormat.PDF_417);
        formats.add(BarcodeFormat.CODE_128);
        formats.add(BarcodeFormat.CODE_39);
        formats.add(BarcodeFormat.EAN_13);
        formats.add(BarcodeFormat.EAN_8);
        formats.add(BarcodeFormat.UPC_A);
        formats.add(BarcodeFormat.UPC_E);
        formats.add(BarcodeFormat.ITF);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        return hints;
    }

    /** Reutiliza la matriz de luminancia ya calculada en vez de recalcularla por binarizador. */
    private static final class MatrixSource extends LuminanceSource {
        private final byte[] data;

        MatrixSource(byte[] data, int width, int height) {
            super(width, height);
            this.data = data;
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            int w = getWidth();
            if (row == null || row.length < w) {
                row = new byte[w];
            }
            System.arraycopy(data, y * w, row, 0, w);
            return row;
        }

        @Override
        public byte[] getMatrix() {
            return data;
        }
    }
}
