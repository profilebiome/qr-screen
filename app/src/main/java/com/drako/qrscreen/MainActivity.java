package com.drako.qrscreen;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla principal: iniciar el escáner de pantalla, leer imágenes e historial. */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 41;
    private static final int REQ_NOTIFICATIONS = 42;
    private static final int REQ_PROJECTION = 43;
    private static final int REQ_PICK_IMAGE = 44;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService io;

    private ScrollView scrollView;
    private LinearLayout column;
    private BroadcastReceiver receiver;
    private Dialog activeDialog;
    private boolean pendingStart;
    private boolean pickInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        io = Executors.newSingleThreadExecutor();
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildRoot());
        registerBus();
        refresh();
        main.post(() -> handleIncoming(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        Updater.maybeCheck(this);
    }

    @Override
    protected void onDestroy() {
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
            receiver = null;
        }
        if (io != null) {
            io.shutdownNow();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ estructura

    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A1220, 0xFF070A12}));

        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Ui.px(this, 300));
        g.setColors(new int[]{0x2E34D399, 0x00070A12});
        glow.setBackground(g);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.px(this, 560), Gravity.TOP);
        glowLp.topMargin = -Ui.px(this, 260);
        root.addView(glow, glowLp);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        column = Ui.column(this);
        column.setPadding(Ui.px(this, 18), Ui.px(this, 14), Ui.px(this, 18), Ui.px(this, 34));
        scrollView.addView(column, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void refresh() {
        if (column == null || isFinishing()) {
            return;
        }
        int scrollY = scrollView.getScrollY();
        column.removeAllViews();
        addHeader();
        addHero();
        addTiles();
        addSavedBar();
        if (ScreenScanService.RUNNING) {
            addActiveCard();
        }
        if (!Settings.canDrawOverlays(this)) {
            addOverlayPermissionCard();
        }
        addHowItWorks();
        addHistory();
        addFooter();
        scrollView.post(() -> {
            View child = scrollView.getChildAt(0);
            if (child != null) {
                scrollView.scrollTo(0, Math.min(scrollY, Math.max(0, child.getHeight() - scrollView.getHeight())));
            }
        });
    }

    private void add(View v, float topDp) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(this, topDp);
        column.addView(v, lp);
    }

    private void addHeader() {
        LinearLayout header = Ui.row(this);
        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 13)));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_logo_mark);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.px(this, 8);
        mark.setPadding(pad, pad, pad, pad);
        logo.addView(mark, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        header.addView(logo, new LinearLayout.LayoutParams(Ui.px(this, 44), Ui.px(this, 44)));

        LinearLayout titles = Ui.column(this);
        TextView name = Ui.bold(this, "QR Screen", 19f, Ui.TEXT);
        name.setLetterSpacing(0.01f);
        titles.addView(name);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginStart(Ui.px(this, 12));
        header.addView(titles, titlesLp);
        add(header, 4);
    }

    private void addHero() {
        boolean running = ScreenScanService.RUNNING;

        LinearLayout hero = Ui.column(this);
        hero.setPadding(Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 18));
        GradientDrawable bg = Ui.gradient(0xFF16273F, 0xFF0D1626, Ui.dp(this, 24));
        bg.setStroke(Math.max(1, Ui.px(this, 1)), 0xFF23395A);
        hero.setBackground(bg);

        TextView title = Ui.bold(this, "Escanea el QR que ya está\nen tu pantalla", 21f, Ui.TEXT);
        title.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams titleLp = Ui.matchWrap();
        titleLp.topMargin = Ui.px(this, 12);
        hero.addView(title, titleLp);

        TextView body = Ui.text(this,
                "Detecta el código que se ve en cualquier app, navegador, PDF o video. La app no abre la cámara: lee los píxeles de la pantalla.",
                12.5f, 0xFFA8B6C9);
        body.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams bodyLp = Ui.matchWrap();
        bodyLp.topMargin = Ui.px(this, 8);
        hero.addView(body, bodyLp);

        HeroArtView art = new HeroArtView(this);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(this, 118));
        artLp.topMargin = Ui.px(this, 6);
        hero.addView(art, artLp);

        TextView action = Ui.button(this, running ? "Detener escáner" : "Escanear pantalla", 15.5f);
        if (running) {
            action.setBackground(Ui.ripple(Ui.gradient(0xFFF87171, 0xFFDC2626, Ui.dp(this, 16)), 0x55000000));
            action.setTextColor(0xFF1E0808);
        }
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(this, 54));
        actionLp.topMargin = Ui.px(this, 10);
        action.setOnClickListener(v -> {
            if (ScreenScanService.RUNNING) {
                stopScanner();
            } else {
                startScanFlow();
            }
        });
        hero.addView(action, actionLp);

        if (running) {
            TextView note = Ui.text(this, "Toca Detener para finalizar la captura.", 10.5f, 0xFF8291A6);
            note.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams noteLp = Ui.matchWrap();
            noteLp.topMargin = Ui.px(this, 9);
            hero.addView(note, noteLp);
        }

        add(hero, 16);
    }

    private void addTiles() {
        int saved = QrStore.list(this).size();
        int scanned = HistoryStore.list(this).size();

        LinearLayout first = Ui.row(this);
        LinearLayout create = tile(R.drawable.ic_create, "Crear QR", "Nombre, enlace y vista previa", true);
        create.setOnClickListener(v -> startActivity(new Intent(this, CreateQrActivity.class)));
        first.addView(create, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout mine = tile(R.drawable.ic_collection, "Mis códigos",
                saved == 0 ? "Tu galería" : (saved == 1 ? "1 guardado" : saved + " guardados"), false);
        mine.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        LinearLayout.LayoutParams mineLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mineLp.setMarginStart(Ui.px(this, 10));
        first.addView(mine, mineLp);
        add(first, 12);

        LinearLayout second = Ui.row(this);
        LinearLayout gallery = tile(R.drawable.ic_gallery, "Desde imagen", "Galería o captura", false);
        gallery.setOnClickListener(v -> pickImage());
        second.addView(gallery, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout history = tile(R.drawable.ic_history, "Historial",
                scanned == 0 ? "Sin lecturas" : (scanned == 1 ? "1 lectura" : scanned + " lecturas"), false);
        history.setOnClickListener(v -> showHistoryDialog());
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        historyLp.setMarginStart(Ui.px(this, 10));
        second.addView(history, historyLp);

        LinearLayout.LayoutParams secondLp = Ui.matchWrap();
        secondLp.topMargin = Ui.px(this, 10);
        column.addView(second, secondLp);
    }

    /** Tira horizontal con los códigos guardados; un toque abre la galería. */
    private void addSavedBar() {
        final java.util.List<QrStore.Entry> saved = QrStore.list(this);
        if (saved.isEmpty()) {
            return;
        }
        LinearLayout head = Ui.row(this);
        head.addView(Ui.bold(this, "Mis códigos", 14.5f, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = Ui.bold(this, "Ver todos", 11.5f, Ui.ACCENT);
        all.setPadding(Ui.px(this, 10), Ui.px(this, 6), Ui.px(this, 4), Ui.px(this, 6));
        all.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
        head.addView(all, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        add(head, 18);

        android.widget.HorizontalScrollView hscroll = new android.widget.HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setClipToPadding(false);
        LinearLayout row = Ui.row(this);

        int shown = Math.min(saved.size(), 12);
        for (int i = 0; i < shown; i++) {
            final QrStore.Entry entry = saved.get(i);
            LinearLayout item = Ui.column(this);
            item.setPadding(0, 0, 0, 0);

            ImageView qr = new ImageView(this);
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
            qr.setBackground(Ui.round(0xFFF6F2E9, Ui.dp(this, 12)));
            int pad = Ui.px(this, 6);
            qr.setPadding(pad, pad, pad, pad);
            qr.setImageBitmap(QrRenderer.create(entry.content, 200));
            item.addView(qr, new LinearLayout.LayoutParams(Ui.px(this, 78), Ui.px(this, 78)));

            TextView label = Ui.text(this, entry.name, 9.5f, Ui.TEXT_DIM);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    Ui.px(this, 78), LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = Ui.px(this, 5);
            item.addView(label, labelLp);

            item.setOnClickListener(v -> startActivity(new Intent(this, GalleryActivity.class)));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    Ui.px(this, 78), LinearLayout.LayoutParams.WRAP_CONTENT);
            itemLp.setMarginEnd(Ui.px(this, 10));
            row.addView(item, itemLp);
        }
        hscroll.addView(row, new android.widget.HorizontalScrollView.LayoutParams(
                android.widget.HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                android.widget.HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hLp = Ui.matchWrap();
        hLp.topMargin = Ui.px(this, 10);
        column.addView(hscroll, hLp);
    }

    private LinearLayout tile(int iconRes, String title, String hint, boolean accent) {
        LinearLayout tile = Ui.column(this);
        tile.setPadding(Ui.px(this, 14), Ui.px(this, 14), Ui.px(this, 14), Ui.px(this, 14));
        if (accent) {
            GradientDrawable bg = Ui.gradient(0xFF14342A, 0xFF0E2119, Ui.dp(this, 18));
            bg.setStroke(Math.max(1, Ui.px(this, 1)), 0xFF246B50);
            tile.setBackground(Ui.ripple(bg, 0x33FFFFFF));
        } else {
            tile.setBackground(Ui.ripple(Ui.round(0xFF101827, Ui.dp(this, 18), 0xFF1E2A3D,
                    Math.max(1, Ui.px(this, 1))), 0x33FFFFFF));
        }
        tile.setClickable(true);
        tile.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.px(this, 24), Ui.px(this, 24));
        tile.addView(icon, iconLp);

        TextView label = Ui.bold(this, title, 13.5f, Ui.TEXT);
        LinearLayout.LayoutParams labelLp = Ui.matchWrap();
        labelLp.topMargin = Ui.px(this, 10);
        tile.addView(label, labelLp);

        TextView sub = Ui.text(this, hint, 11f, Ui.TEXT_DIM);
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 2);
        tile.addView(sub, subLp);
        return tile;
    }

    private void addActiveCard() {
        LinearLayout card = Ui.row(this);
        card.setPadding(Ui.px(this, 14), Ui.px(this, 12), Ui.px(this, 12), Ui.px(this, 12));
        card.setBackground(Ui.round(0xFF0F1F1A, Ui.dp(this, 18), 0xFF1E4A38, Math.max(1, Ui.px(this, 1))));

        View dot = new View(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(Ui.ACCENT);
        dot.setBackground(oval);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.px(this, 9), Ui.px(this, 9));
        dotLp.setMarginEnd(Ui.px(this, 10));
        card.addView(dot, dotLp);

        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.bold(this, "Escáner activo", 13.5f, 0xFFDFF7EC));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 2);
        texts.addView(Ui.text(this, "El visor está sobre tus apps", 11f, 0xFF8FBFAE), subLp);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView stop = Ui.ghostButton(this, "Detener", 12f, Ui.DANGER);
        stop.setOnClickListener(v -> stopScanner());
        card.addView(stop, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        add(card, 12);
    }

    private void addOverlayPermissionCard() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 16), Ui.px(this, 14), Ui.px(this, 16), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF221A10, Ui.dp(this, 18), 0xFF4A3A1C, Math.max(1, Ui.px(this, 1))));

        card.addView(Ui.bold(this, "Falta el permiso de visor", 13.5f, 0xFFFBD38D));
        TextView desc = Ui.text(this,
                "Para dibujar el marco y los resultados encima de otras apps, activa «Mostrar sobre otras apps».",
                11.5f, 0xFFC9AE87);
        desc.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams descLp = Ui.matchWrap();
        descLp.topMargin = Ui.px(this, 4);
        card.addView(desc, descLp);

        TextView grant = Ui.ghostButton(this, "Conceder permiso", 12f, 0xFFFBD38D);
        LinearLayout.LayoutParams grantLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        grantLp.topMargin = Ui.px(this, 10);
        grant.setOnClickListener(v -> openOverlaySettings());
        card.addView(grant, grantLp);
        add(card, 12);
    }

    private void addHowItWorks() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 16), Ui.px(this, 16), Ui.px(this, 16), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF0D1524, Ui.dp(this, 18), 0xFF1B2740, Math.max(1, Ui.px(this, 1))));
        card.addView(Ui.bold(this, "Cómo funciona", 14f, Ui.TEXT));

        addStep(card, 1, "Toca «Escanear pantalla» y acepta la captura.");
        addStep(card, 2, "Abre el QR en cualquier app: el visor aparece arriba.");
        addStep(card, 3, "Al detectarlo verás la tarjeta para copiar, abrir o compartir.");
        add(card, 14);
    }

    private void addStep(LinearLayout parent, int number, String text) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams rowLp = Ui.matchWrap();
        rowLp.topMargin = Ui.px(this, 12);

        TextView badge = Ui.bold(this, String.valueOf(number), 11f, Ui.ACCENT);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(0x1F34D399, Ui.dp(this, 20)));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.px(this, 22), Ui.px(this, 22)));

        TextView label = Ui.text(this, text, 12f, 0xFFB9C6D6);
        label.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(Ui.px(this, 10));
        row.addView(label, labelLp);
        parent.addView(row, rowLp);
    }

    private void addHistory() {
        List<HistoryStore.Item> items = HistoryStore.list(this);

        LinearLayout head = Ui.row(this);
        head.addView(Ui.bold(this, "Recientes", 14.5f, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!items.isEmpty()) {
            TextView clear = Ui.bold(this, "Borrar", 11.5f, Ui.TEXT_DIM);
            clear.setPadding(Ui.px(this, 10), Ui.px(this, 6), Ui.px(this, 10), Ui.px(this, 6));
            clear.setBackground(Ui.ripple(Ui.round(0x00000000, Ui.dp(this, 10)), 0x33FFFFFF));
            clear.setOnClickListener(v -> confirmClearHistory());
            head.addView(clear, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        add(head, 20);

        if (items.isEmpty()) {
            LinearLayout empty = Ui.column(this);
            empty.setPadding(Ui.px(this, 16), Ui.px(this, 18), Ui.px(this, 16), Ui.px(this, 18));
            empty.setBackground(Ui.round(0xFF0B1220, Ui.dp(this, 16),
                    0x331E2A3D, Math.max(1, Ui.px(this, 1))));
            TextView t = Ui.text(this, "Aún no hay lecturas.\nEscanea algo y aparecerá aquí.", 12f, Ui.TEXT_DIM);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(Ui.dp(this, 3), 1f);
            empty.addView(t, Ui.matchWrap());
            add(empty, 10);
            return;
        }

        int shown = Math.min(items.size(), 5);
        for (int i = 0; i < shown; i++) {
            final HistoryStore.Item item = items.get(i);
            LinearLayout row = historyRow(item);
            row.setOnClickListener(v -> showResultDialog(item.text, item.format));
            add(row, 10);
        }

        if (items.size() > shown) {
            TextView all = Ui.ghostButton(this, "Ver historial completo (" + items.size() + ")", 12f, Ui.TEXT);
            LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            allLp.topMargin = Ui.px(this, 10);
            all.setOnClickListener(v -> showHistoryDialog());
            column.addView(all, allLp);
        }
    }

    private LinearLayout historyRow(HistoryStore.Item item) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.px(this, 12), Ui.px(this, 11), Ui.px(this, 12), Ui.px(this, 11));
        row.setBackground(Ui.ripple(Ui.round(0xFF0D1524, Ui.dp(this, 15), 0xFF1B2740,
                Math.max(1, Ui.px(this, 1))), 0x22FFFFFF));
        row.setClickable(true);
        row.setFocusable(true);

        TextView badge = Ui.bold(this, shortFormat(item.format), 9f, Ui.ACCENT);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.round(0x1F34D399, Ui.dp(this, 10)));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.px(this, 38), Ui.px(this, 34)));

        LinearLayout texts = Ui.column(this);
        TextView text = Ui.text(this, preview(item.text), 12.5f, Ui.TEXT);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(text);
        TextView meta = Ui.text(this, HistoryStore.formatLabel(item.format) + " · "
                + HistoryStore.relativeTime(item.time) + " · " + item.source, 10.5f, Ui.TEXT_DIM);
        LinearLayout.LayoutParams metaLp = Ui.matchWrap();
        metaLp.topMargin = Ui.px(this, 2);
        texts.addView(meta, metaLp);

        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMarginStart(Ui.px(this, 10));
        row.addView(texts, textsLp);

        TextView arrow = Ui.bold(this, "›", 18f, 0xFF5B6B82);
        row.addView(arrow);
        return row;
    }

    private String shortFormat(String format) {
        String label = HistoryStore.formatLabel(format);
        return label.length() > 4 ? label.substring(0, 4) : label;
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String one = text.replace('\n', ' ').trim();
        return one.length() > 90 ? one.substring(0, 90) + "…" : one;
    }

    private void addFooter() {
        TextView check = Ui.bold(this, "Buscar actualizaciones", 11f, Ui.ACCENT);
        check.setGravity(Gravity.CENTER);
        check.setPadding(0, Ui.px(this, 26), 0, Ui.px(this, 4));
        check.setOnClickListener(v -> Updater.check(this, true));
        column.addView(check, Ui.matchWrap());
    }

    // ------------------------------------------------------------------ escaneo

    private void startScanFlow() {
        if (ScreenScanService.RUNNING) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        launchProjection();
    }

    private void launchProjection() {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) {
                Actions.toast(this, "Captura de pantalla no disponible");
                return;
            }
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
        } catch (Throwable t) {
            Actions.toast(this, "No se pudo abrir el permiso de captura");
        }
    }

    private void stopScanner() {
        try {
            Intent i = new Intent(this, ScreenScanService.class).setAction(ScreenScanService.ACTION_STOP);
            startService(i);
        } catch (Throwable ignored) {
        }
        Actions.toast(this, "Escáner detenido");
        main.postDelayed(this::refresh, 400);
    }

    private void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
                Actions.toast(this, "Abre Ajustes › Apps › Acceso especial");
            }
        }
    }

    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso de visor")
                .setMessage("Para dibujar el marco de escaneo y la tarjeta de resultados sobre otras apps, "
                        + "QR Screen necesita el permiso «Mostrar sobre otras apps».")
                .setPositiveButton("Conceder", (d, w) -> openOverlaySettings())
                .setNegativeButton("Ahora no", null)
                .show();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBus() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }
                if (ScreenScanService.ACTION_STATE.equals(intent.getAction())) {
                    int state = intent.getIntExtra(ScreenScanService.EXTRA_STATE, ScreenScanService.STATE_IDLE);
                    if (state == ScreenScanService.STATE_ERROR) {
                        String message = intent.getStringExtra(ScreenScanService.EXTRA_MESSAGE);
                        if (message != null) {
                            Actions.toast(MainActivity.this, message);
                        }
                    }
                    refresh();
                } else if (ScreenScanService.ACTION_RESULT.equals(intent.getAction())) {
                    refresh();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenScanService.ACTION_STATE);
        filter.addAction(ScreenScanService.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    // ------------------------------------------------------------------ imágenes

    private void pickImage() {
        if (pickInFlight) {
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, REQ_PICK_IMAGE);
        } catch (Throwable t) {
            Actions.toast(this, "No hay galería disponible");
        }
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) {
            decodeImage(uri);
            intent.setAction(null);
        }
    }

    private void decodeImage(Uri uri) {
        if (uri == null || io == null) {
            return;
        }
        pickInFlight = true;
        final Dialog progress = buildProgressDialog("Leyendo imagen…");
        try {
            progress.show();
        } catch (Throwable ignored) {
        }
        io.execute(() -> {
            QrDecoder.Hit hit = QrImage.decode(MainActivity.this, uri);
            main.post(() -> {
                pickInFlight = false;
                try {
                    progress.dismiss();
                } catch (Throwable ignored) {
                }
                if (hit != null && hit.text != null) {
                    HistoryStore.add(MainActivity.this, hit.text, hit.format, HistoryStore.SOURCE_IMAGE);
                    Actions.feedback(MainActivity.this);
                    refresh();
                    showResultDialog(hit.text, hit.format);
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Sin código legible")
                            .setMessage("No se encontró un QR o código de barras en esa imagen. "
                                    + "Prueba con una captura más nítida o recórtala.")
                            .setPositiveButton("Entendido", null)
                            .show();
                }
            });
        });
    }

    private Dialog buildProgressDialog(String message) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = Ui.row(this);
        card.setPadding(Ui.px(this, 18), Ui.px(this, 16), Ui.px(this, 18), Ui.px(this, 16));
        card.setBackground(Ui.round(0xFF101827, Ui.dp(this, 18), 0xFF26364F, Math.max(1, Ui.px(this, 1))));
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        card.addView(bar, new LinearLayout.LayoutParams(Ui.px(this, 22), Ui.px(this, 22)));
        TextView text = Ui.text(this, message, 13f, Ui.TEXT);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(Ui.px(this, 12));
        card.addView(text, textLp);

        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(wrap);
        dialog.setCancelable(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.5f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }

    // ------------------------------------------------------------------ resultados

    private void showResultDialog(String text, String format) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF101827, Ui.dp(this, 24), 0xFF26364F, Math.max(1, Ui.px(this, 1))));

        LinearLayout header = Ui.row(this);
        TextView check = Ui.bold(this, "✓", 16f, 0xFF07130F);
        check.setGravity(Gravity.CENTER);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(Ui.ACCENT);
        check.setBackground(oval);
        header.addView(check, new LinearLayout.LayoutParams(Ui.px(this, 34), Ui.px(this, 34)));

        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.bold(this, "Código detectado", 15.5f, Ui.TEXT));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 2);
        titles.addView(Ui.text(this, HistoryStore.formatLabel(format), 11f, Ui.TEXT_DIM), subLp);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginStart(Ui.px(this, 10));
        header.addView(titles, titlesLp);
        card.addView(header, Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView content = Ui.text(this, text, 14.5f, Ui.TEXT);
        content.setTextIsSelectable(true);
        content.setLineSpacing(Ui.dp(this, 3), 1f);
        content.setPadding(Ui.px(this, 14), Ui.px(this, 13), Ui.px(this, 14), Ui.px(this, 13));
        content.setBackground(Ui.round(0xFF0B1120, Ui.dp(this, 14),
                0xFF1D2A42, Math.max(1, Ui.px(this, 1))));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int contentH = Math.min(Ui.px(this, 230), Math.round(screenH * 0.30f));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, contentH);
        scrollLp.topMargin = Ui.px(this, 14);
        card.addView(scroll, scrollLp);

        LinearLayout buttons = Ui.row(this);
        LinearLayout.LayoutParams buttonsLp = Ui.matchWrap();
        buttonsLp.topMargin = Ui.px(this, 14);

        TextView copy = Ui.button(this, "Copiar", 13f);
        copy.setOnClickListener(v -> Actions.copy(this, text));
        buttons.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (Actions.openable(text)) {
            TextView open = Ui.ghostButton(this, "Abrir", 13f, Ui.TEXT);
            open.setOnClickListener(v -> Actions.open(this, text));
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            openLp.setMarginStart(Ui.px(this, 8));
            buttons.addView(open, openLp);
        }

        TextView share = Ui.ghostButton(this, "Compartir", 13f, Ui.TEXT);
        share.setOnClickListener(v -> Actions.share(this, text));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        shareLp.setMarginStart(Ui.px(this, 8));
        buttons.addView(share, shareLp);
        card.addView(buttons, buttonsLp);

        showCardDialog(card);
    }

    private void showHistoryDialog() {
        List<HistoryStore.Item> items = HistoryStore.list(this);
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 18), Ui.px(this, 16));
        card.setBackground(Ui.round(0xFF101827, Ui.dp(this, 24), 0xFF26364F, Math.max(1, Ui.px(this, 1))));

        LinearLayout head = Ui.row(this);
        head.addView(Ui.bold(this, "Historial", 16f, Ui.TEXT),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = Ui.bold(this, String.valueOf(items.size()), 11f, Ui.ACCENT);
        count.setGravity(Gravity.CENTER);
        count.setBackground(Ui.round(0x1F34D399, Ui.dp(this, 20)));
        count.setPadding(Ui.px(this, 9), Ui.px(this, 3), Ui.px(this, 9), Ui.px(this, 3));
        head.addView(count);
        card.addView(head, Ui.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = Ui.column(this);
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, "Todavía no hay lecturas.", 12.5f, Ui.TEXT_DIM);
            empty.setPadding(Ui.px(this, 4), Ui.px(this, 16), Ui.px(this, 4), Ui.px(this, 16));
            list.addView(empty);
        } else {
            for (HistoryStore.Item item : items) {
                LinearLayout row = historyRow(item);
                row.setOnClickListener(v -> {
                    dismissAllDialogs();
                    main.postDelayed(() -> showResultDialog(item.text, item.format), 200);
                });
                LinearLayout.LayoutParams rowLp = Ui.matchWrap();
                rowLp.topMargin = Ui.px(this, 8);
                list.addView(row, rowLp);
            }
        }
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int listH = Math.min(Ui.px(this, 280), Math.round(screenH * 0.38f));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listH);
        scrollLp.topMargin = Ui.px(this, 12);
        card.addView(scroll, scrollLp);

        if (!items.isEmpty()) {
            TextView clear = Ui.ghostButton(this, "Borrar historial", 12f, Ui.DANGER);
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clearLp.topMargin = Ui.px(this, 12);
            clear.setOnClickListener(v -> {
                dismissAllDialogs();
                confirmClearHistory();
            });
            card.addView(clear, clearLp);
        }
        showCardDialog(card);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar historial?")
                .setMessage("Se eliminarán todas las lecturas guardadas en este dispositivo.")
                .setPositiveButton("Borrar", (d, w) -> {
                    HistoryStore.clear(this);
                    refresh();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void dismissAllDialogs() {
        if (activeDialog != null) {
            try {
                activeDialog.dismiss();
            } catch (Throwable ignored) {
            }
            activeDialog = null;
        }
    }

    private void showCardDialog(LinearLayout card) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.px(this, 14), Ui.px(this, 24), Ui.px(this, 14), Ui.px(this, 26));
        wrap.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrap);
        activeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });
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
        dialog.show();
    }

    // ------------------------------------------------------------------ resultados de sistema

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            refresh();
            if (Settings.canDrawOverlays(this) && !ScreenScanService.RUNNING) {
                startScanFlow();
            }
            return;
        }
        if (requestCode == REQ_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                launchScannerService(resultCode, data);
            } else {
                Actions.toast(this, "Captura cancelada");
            }
            return;
        }
        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                decodeImage(data.getData());
            }
            return;
        }
        refresh();
    }

    private void launchScannerService(int resultCode, Intent data) {
        try {
            Intent service = new Intent(this, ScreenScanService.class)
                    .setAction(ScreenScanService.ACTION_START)
                    .putExtra(ScreenScanService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(ScreenScanService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            Actions.toast(this, "Escáner activo · abre el QR en cualquier app");
            main.postDelayed(this::refresh, 500);
            main.postDelayed(() -> {
                try {
                    moveTaskToBack(true);
                } catch (Throwable ignored) {
                }
            }, 1300);
        } catch (Throwable t) {
            Actions.toast(this, "No se pudo iniciar el escáner");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            refresh();
            if (pendingStart) {
                pendingStart = false;
                launchProjection();
            }
        }
    }
}
