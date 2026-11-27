package com.drako.qrscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Galería de los códigos creados: nombre, tipo, contenido y acciones. */
public class GalleryActivity extends Activity {

    private final List<QrStore.Entry> items = new ArrayList<>();
    private final LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(12) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    private GridView grid;
    private TextView countLabel;
    private View emptyState;
    private Adapter adapter;
    private Dialog openDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(build());
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        if (openDialog != null) {
            try {
                openDialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
        bitmapCache.evictAll();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ ui

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A1220, 0xFF070A12}));

        LinearLayout col = Ui.column(this);
        col.setPadding(0, Ui.px(this, 10), 0, 0);
        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = Ui.row(this);
        header.setPadding(Ui.px(this, 18), 0, Ui.px(this, 18), 0);
        TextView back = Ui.bold(this, "‹", 22f, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(Ui.round(0xFF14203A, Ui.dp(this, 12)), 0x33FFFFFF));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.px(this, 38), Ui.px(this, 38)));

        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.bold(this, "Mis códigos", 18f, Ui.TEXT));
        countLabel = Ui.text(this, "0 guardados", 11.5f, Ui.TEXT_DIM);
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 1);
        titles.addView(countLabel, subLp);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginStart(Ui.px(this, 12));
        header.addView(titles, titlesLp);

        TextView add = Ui.ghostButton(this, "+ Nuevo", 12f, Ui.ACCENT);
        add.setOnClickListener(v -> startActivity(new Intent(this, CreateQrActivity.class)));
        header.addView(add, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(header, Ui.matchWrap());

        FrameLayout body = new FrameLayout(this);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.topMargin = Ui.px(this, 14);
        col.addView(body, bodyLp);

        grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(Ui.px(this, 10));
        grid.setVerticalSpacing(Ui.px(this, 10));
        grid.setPadding(Ui.px(this, 18), 0, Ui.px(this, 18), Ui.px(this, 28));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        body.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        emptyState = buildEmptyState();
        body.addView(emptyState, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    private View buildEmptyState() {
        LinearLayout box = Ui.column(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.px(this, 30), Ui.px(this, 24), Ui.px(this, 30), Ui.px(this, 24));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_collection);
        box.addView(icon, new LinearLayout.LayoutParams(Ui.px(this, 40), Ui.px(this, 40)));

        TextView title = Ui.bold(this, "Todavía no hay códigos", 15f, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = Ui.matchWrap();
        titleLp.topMargin = Ui.px(this, 12);
        box.addView(title, titleLp);

        TextView text = Ui.text(this, "Crea un QR con nombre y enlace, y quedará aquí con su información.",
                12f, Ui.TEXT_DIM);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams textLp = Ui.matchWrap();
        textLp.topMargin = Ui.px(this, 6);
        box.addView(text, textLp);

        TextView create = Ui.button(this, "Crear el primero", 13.5f);
        create.setOnClickListener(v -> startActivity(new Intent(this, CreateQrActivity.class)));
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = Ui.px(this, 16);
        box.addView(create, createLp);
        return box;
    }

    private void reload() {
        if (grid == null) {
            return;
        }
        items.clear();
        items.addAll(QrStore.list(this));
        adapter.notifyDataSetChanged();
        countLabel.setText(items.size() == 1 ? "1 guardado" : items.size() + " guardados");
        emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        grid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ grid

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            View view = convertView;
            if (view == null) {
                view = buildCard();
                holder = (Holder) view.getTag();
            } else {
                holder = (Holder) view.getTag();
            }
            QrStore.Entry entry = items.get(position);
            holder.name.setText(entry.name);
            holder.meta.setText(QrStore.typeLabel(entry.type) + " · " + QrStore.when(entry.createdAt));
            holder.qr.setImageBitmap(thumb(entry));
            view.setOnClickListener(v -> showDetail(entry));
            return view;
        }
    }

    private static final class Holder {
        final ImageView qr;
        final TextView name;
        final TextView meta;

        Holder(View root) {
            qr = root.findViewWithTag("qr");
            name = root.findViewWithTag("name");
            meta = root.findViewWithTag("meta");
        }
    }

    private View buildCard() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 10), Ui.px(this, 10), Ui.px(this, 10), Ui.px(this, 12));
        card.setBackground(Ui.ripple(Ui.round(0xFF101827, Ui.dp(this, 16), 0xFF1E2A3D,
                Math.max(1, Ui.px(this, 1))), 0x33FFFFFF));
        card.setClickable(true);

        ImageView qr = new ImageView(this);
        qr.setTag("qr");
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setBackground(Ui.round(0xFFF6F2E9, Ui.dp(this, 11)));
        int pad = Ui.px(this, 7);
        qr.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(this, 104));
        card.addView(qr, qrLp);

        TextView name = Ui.bold(this, "", 12.5f, Ui.TEXT);
        name.setTag("name");
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = Ui.matchWrap();
        nameLp.topMargin = Ui.px(this, 9);
        card.addView(name, nameLp);

        TextView meta = Ui.text(this, "", 10.5f, Ui.TEXT_DIM);
        meta.setTag("meta");
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = Ui.matchWrap();
        metaLp.topMargin = Ui.px(this, 2);
        card.addView(meta, metaLp);

        card.setTag(new Holder(card));
        return card;
    }

    private Bitmap thumb(QrStore.Entry entry) {
        String key = entry.id + "|" + entry.content;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap bmp = QrRenderer.create(entry.content, 360);
        if (bmp != null) {
            bitmapCache.put(key, bmp);
        }
        return bmp;
    }

    // ------------------------------------------------------------------ detalle

    private void showDetail(QrStore.Entry entry) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF101827, Ui.dp(this, 24), 0xFF26364F, Math.max(1, Ui.px(this, 1))));

        FrameLayout holder = new FrameLayout(this);
        holder.setBackground(Ui.round(0xFFF6F2E9, Ui.dp(this, 18)));
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(Ui.px(this, 224), Ui.px(this, 224));
        holderLp.gravity = Gravity.CENTER_HORIZONTAL;
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.px(this, 12);
        image.setPadding(pad, pad, pad, pad);
        image.setImageBitmap(QrRenderer.create(entry.content, 700));
        holder.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(holder, holderLp);

        TextView name = Ui.bold(this, entry.name, 16f, Ui.TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = Ui.matchWrap();
        nameLp.topMargin = Ui.px(this, 14);
        card.addView(name, nameLp);

        TextView meta = Ui.text(this, QrStore.typeLabel(entry.type) + " · " + QrStore.when(entry.createdAt),
                11f, Ui.TEXT_DIM);
        meta.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams metaLp = Ui.matchWrap();
        metaLp.topMargin = Ui.px(this, 3);
        card.addView(meta, metaLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView content = Ui.text(this, entry.content, 12.5f, 0xFFDCE6F2);
        content.setTextIsSelectable(true);
        content.setLineSpacing(Ui.dp(this, 2), 1f);
        content.setPadding(Ui.px(this, 12), Ui.px(this, 10), Ui.px(this, 12), Ui.px(this, 10));
        content.setBackground(Ui.round(0xFF0B1120, Ui.dp(this, 12),
                0xFF1D2A42, Math.max(1, Ui.px(this, 1))));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.min(Ui.px(this, 96),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.14f)));
        scrollLp.topMargin = Ui.px(this, 12);
        card.addView(scroll, scrollLp);

        LinearLayout row1 = Ui.row(this);
        LinearLayout.LayoutParams row1Lp = Ui.matchWrap();
        row1Lp.topMargin = Ui.px(this, 12);
        TextView copy = Ui.button(this, "Copiar", 12.5f);
        copy.setOnClickListener(v -> Actions.copy(this, entry.content));
        row1.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (Actions.openable(entry.content)) {
            TextView open = Ui.ghostButton(this, "Abrir", 12.5f, Ui.TEXT);
            open.setOnClickListener(v -> Actions.open(this, entry.content));
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            openLp.setMarginStart(Ui.px(this, 8));
            row1.addView(open, openLp);
        }

        TextView share = Ui.ghostButton(this, "Compartir", 12.5f, Ui.TEXT);
        share.setOnClickListener(v -> sharePng(entry));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        shareLp.setMarginStart(Ui.px(this, 8));
        row1.addView(share, shareLp);
        card.addView(row1, row1Lp);

        LinearLayout row2 = Ui.row(this);
        LinearLayout.LayoutParams row2Lp = Ui.matchWrap();
        row2Lp.topMargin = Ui.px(this, 8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TextView save = Ui.ghostButton(this, "A la galería", 12f, Ui.TEXT);
            save.setOnClickListener(v -> saveToGallery(entry));
            row2.addView(save, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        TextView rename = Ui.ghostButton(this, "Renombrar", 12f, Ui.TEXT);
        rename.setOnClickListener(v -> renameDialog(entry));
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row2.getChildCount() > 0) {
            renameLp.setMarginStart(Ui.px(this, 8));
        }
        row2.addView(rename, renameLp);

        TextView delete = Ui.ghostButton(this, "Eliminar", 12f, Ui.DANGER);
        delete.setOnClickListener(v -> deleteDialog(entry));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        deleteLp.setMarginStart(Ui.px(this, 8));
        row2.addView(delete, deleteLp);
        card.addView(row2, row2Lp);

        openDialog = showCardDialog(card);
    }

    private Dialog showCardDialog(LinearLayout card) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.px(this, 14), Ui.px(this, 24), Ui.px(this, 14), Ui.px(this, 26));
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.addView(card, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        wrap.addView(scroller, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrap);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0.62f;
                lp.gravity = Gravity.BOTTOM;
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                window.setAttributes(lp);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.setOnDismissListener(d -> {
            if (openDialog == dialog) {
                openDialog = null;
            }
        });
        dialog.show();
        return dialog;
    }

    private void renameDialog(QrStore.Entry entry) {
        EditText input = new EditText(this);
        input.setText(entry.name);
        input.setSelection(input.getText().length());
        input.setTextSize(15f);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(0xFF66758B);
        input.setHint("Nombre del código");
        int pad = Ui.px(this, 16);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Renombrar")
                .setView(input)
                .setPositiveButton("Guardar", (d, w) -> {
                    QrStore.rename(this, entry.id, input.getText().toString());
                    dismissDetail();
                    reload();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteDialog(QrStore.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle("¿Eliminar este QR?")
                .setMessage("Se borrará «" + entry.name + "» de la galería. No se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    QrStore.remove(this, entry.id);
                    bitmapCache.evictAll();
                    dismissDetail();
                    reload();
                    Actions.toast(this, "Código eliminado");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void dismissDetail() {
        if (openDialog != null) {
            try {
                openDialog.dismiss();
            } catch (Throwable ignored) {
            }
            openDialog = null;
        }
    }

    // ------------------------------------------------------------------ exportar

    private void sharePng(QrStore.Entry entry) {
        Bitmap bitmap = QrRenderer.create(entry.content, 1000);
        if (bitmap == null) {
            Actions.toast(this, "No se pudo generar la imagen");
            return;
        }
        try {
            File dir = new File(getCacheDir(), "qr-share");
            if (!dir.exists() && !dir.mkdirs()) {
                Actions.toast(this, "No se pudo preparar la imagen");
                return;
            }
            File file = new File(dir, "qr-" + entry.id + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = QrFileProvider.uriFor(this, file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, entry.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Compartir QR"));
        } catch (Throwable t) {
            Actions.toast(this, "No se pudo compartir la imagen");
        } finally {
            bitmap.recycle();
        }
    }

    private void saveToGallery(QrStore.Entry entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Actions.toast(this, "Necesita Android 10 o superior");
            return;
        }
        Bitmap bitmap = QrRenderer.create(entry.content, 1000);
        if (bitmap == null) {
            Actions.toast(this, "No se pudo generar la imagen");
            return;
        }
        Uri inserted = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, safeFileName(entry.name) + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/QR Screen");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            inserted = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (inserted == null) {
                Actions.toast(this, "No se pudo guardar en la galería");
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(inserted)) {
                if (out == null) {
                    throw new java.io.IOException("sin salida");
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(inserted, done, null, null);
            Actions.toast(this, "Guardado en Pictures/QR Screen");
        } catch (Throwable t) {
            if (inserted != null) {
                try {
                    getContentResolver().delete(inserted, null, null);
                } catch (Throwable ignored) {
                }
            }
            Actions.toast(this, "No se pudo guardar en la galería");
        } finally {
            bitmap.recycle();
        }
    }

    private String safeFileName(String name) {
        String clean = name == null ? "qr" : name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (clean.isEmpty()) {
            clean = "qr";
        }
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }
}
