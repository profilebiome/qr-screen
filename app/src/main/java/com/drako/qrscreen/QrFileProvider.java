package com.drako.qrscreen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;

/**
 * Comparte los PNG generados sin pedir permisos de almacenamiento.
 * Solo sirve archivos de cache/qr-share y solo en lectura.
 */
public class QrFileProvider extends ContentProvider {

    static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".files")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            return null;
        }
        File ctxCache = getContext().getCacheDir();
        for (String folder : new String[]{"qr-share", "update"}) {
            File file = new File(new File(ctxCache, folder), name);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (mode != null && (mode.contains("w") || mode.contains("+") || mode.contains("r+"))) {
            throw new java.io.FileNotFoundException("Solo lectura");
        }
        File file = resolve(uri);
        if (file == null) {
            throw new java.io.FileNotFoundException("No encontrado");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        File file = resolve(uri);
        if (file == null) {
            return new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME}, 0);
        }
        String[] columns;
        if (projection == null || projection.length == 0) {
            columns = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        } else {
            columns = projection;
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(column)) {
                row[i] = file.length();
            } else if ("_id".equals(column)) {
                row[i] = 0;
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
