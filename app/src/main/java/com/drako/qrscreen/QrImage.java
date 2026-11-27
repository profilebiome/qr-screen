package com.drako.qrscreen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

/** Decodifica un código desde una imagen de la galería o compartida por otra app. */
final class QrImage {

    private QrImage() {
    }

    static QrDecoder.Hit decode(Context context, Uri uri) {
        Bitmap bitmap = load(context, uri, 2000);
        if (bitmap == null) {
            return null;
        }
        try {
            QrDecoder.Hit hit = tryScales(bitmap);
            if (hit != null) {
                return hit;
            }
            for (int deg : new int[]{90, 180, 270}) {
                Bitmap rotated = rotate(bitmap, deg);
                hit = tryScales(rotated);
                if (rotated != bitmap) {
                    rotated.recycle();
                }
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    private static QrDecoder.Hit tryScales(Bitmap bitmap) {
        QrDecoder.Hit hit = tryBitmap(bitmap);
        if (hit != null) {
            return hit;
        }
        Bitmap half = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, bitmap.getWidth() / 2), Math.max(1, bitmap.getHeight() / 2), true);
        try {
            hit = tryBitmap(half);
            if (hit != null) {
                return hit;
            }
        } finally {
            if (half != bitmap) {
                half.recycle();
            }
        }
        if (bitmap.getWidth() < 700) {
            Bitmap twice = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() * 2,
                    bitmap.getHeight() * 2, true);
            try {
                return tryBitmap(twice);
            } finally {
                twice.recycle();
            }
        }
        return null;
    }

    private static QrDecoder.Hit tryBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 8 || bitmap.getHeight() < 8) {
            return null;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return QrDecoder.decode(pixels, w, h, true);
    }

    private static Bitmap rotate(Bitmap src, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private static Bitmap load(Context context, Uri uri, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bitmap == null) {
                return null;
            }
            int exifRotation = readExifRotation(context, uri);
            if (exifRotation != 0) {
                Bitmap rotated = rotate(bitmap, exifRotation);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                bitmap = rotated;
            }
            // Ajusta a un tamaño de trabajo razonable
            int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longest > maxDim) {
                float scale = maxDim / (float) longest;
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                }
                bitmap = scaled;
            }
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int readExifRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }
}
