package com.drako.qrscreen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Crear un código QR con nombre y contenido, con vista previa en vivo. */
public class CreateQrActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText contentInput;
    private EditText nameInput;
    private TextView typeChip;
    private ImageView preview;
    private TextView previewHint;
    private TextView saveButton;
    private TextView copyButton;
    private ScrollView scroll;

    private boolean saved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(build());
        updatePreview();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ ui

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A1220, 0xFF070A12}));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.px(this, 18), Ui.px(this, 10), Ui.px(this, 18), Ui.px(this, 30));
        scroll.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        col.addView(buildHeader(), Ui.matchWrap());

        // contenido
        LinearLayout contentCard = card();
        contentCard.addView(label("Contenido o enlace"));
        contentInput = input("https://… o WIFI:S:…;T:WPA;P:…;;", 4);
        LinearLayout.LayoutParams inputLp = Ui.matchWrap();
        inputLp.topMargin = Ui.px(this, 8);
        contentCard.addView(contentInput, inputLp);

        typeChip = Ui.bold(this, "Texto", 10.5f, Ui.ACCENT);
        typeChip.setPadding(Ui.px(this, 9), Ui.px(this, 4), Ui.px(this, 9), Ui.px(this, 4));
        typeChip.setBackground(Ui.round(0x1F34D399, Ui.dp(this, 20)));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = Ui.px(this, 10);
        contentCard.addView(typeChip, chipLp);
        add(col, contentCard, 16);

        // nombre
        LinearLayout nameCard = card();
        nameCard.addView(label("Nombre (opcional)"));
        nameInput = input("Mi enlace, Wi-Fi de casa, tarjeta…", 1);
        LinearLayout.LayoutParams nameLp = Ui.matchWrap();
        nameLp.topMargin = Ui.px(this, 8);
        nameCard.addView(nameInput, nameLp);
        TextView nameHint = Ui.text(this, "Si lo dejas vacío se usa el dominio o el propio texto.", 10.5f, Ui.TEXT_DIM);
        LinearLayout.LayoutParams hintLp = Ui.matchWrap();
        hintLp.topMargin = Ui.px(this, 8);
        nameCard.addView(nameHint, hintLp);
        add(col, nameCard, 10);

        // vista previa
        LinearLayout previewCard = card();
        previewCard.addView(label("Vista previa"));
        FrameLayout holder = new FrameLayout(this);
        holder.setBackground(Ui.round(0xFFF6F2E9, Ui.dp(this, 18)));
        int size = Ui.px(this, 236);
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(size, size);
        holderLp.topMargin = Ui.px(this, 10);
        holderLp.gravity = Gravity.CENTER_HORIZONTAL;

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.px(this, 12);
        preview.setPadding(pad, pad, pad, pad);
        holder.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        previewHint = Ui.text(this, "Escribe algo y el código aparece aquí.", 11.5f, 0xFF7A8798);
        previewHint.setGravity(Gravity.CENTER);
        previewHint.setLineSpacing(Ui.dp(this, 2), 1f);
        FrameLayout.LayoutParams hintCenter = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        hintCenter.setMargins(Ui.px(this, 18), 0, Ui.px(this, 18), 0);
        holder.addView(previewHint, hintCenter);

        previewCard.addView(holder, holderLp);

        TextView previewNote = Ui.text(this, "Este QR es un PNG limpio, listo para imprimir o compartir.",
                10.5f, Ui.TEXT_DIM);
        previewNote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = Ui.matchWrap();
        noteLp.topMargin = Ui.px(this, 10);
        previewCard.addView(previewNote, noteLp);
        add(col, previewCard, 10);

        // acciones
        saveButton = Ui.button(this, "Guardar en mis QR", 15f);
        saveButton.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(this, 52));
        saveLp.topMargin = Ui.px(this, 16);
        col.addView(saveButton, saveLp);

        copyButton = Ui.ghostButton(this, "Copiar contenido", 13f, Ui.TEXT);
        copyButton.setOnClickListener(v -> {
            String text = contentInput.getText().toString().trim();
            if (!text.isEmpty()) {
                Actions.copy(this, text);
            }
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        copyLp.topMargin = Ui.px(this, 8);
        col.addView(copyButton, copyLp);

        TextView privacy = Ui.text(this, "Se guarda solo en este teléfono. Sin internet ni cuenta.",
                10.5f, Ui.TEXT_DIM);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyLp = Ui.matchWrap();
        privacyLp.topMargin = Ui.px(this, 14);
        col.addView(privacy, privacyLp);

        contentInput.addTextChangedListener(new Watcher(this::scheduleUpdate));
        nameInput.addTextChangedListener(new Watcher(this::scheduleUpdate));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = Ui.row(this);
        TextView back = Ui.bold(this, "‹", 22f, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(Ui.round(0xFF14203A, Ui.dp(this, 12)), 0x33FFFFFF));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.px(this, 38), Ui.px(this, 38)));

        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.bold(this, "Crear QR", 18f, Ui.TEXT));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 1);
        titles.addView(Ui.text(this, "Nombre, contenido y vista previa", 11.5f, Ui.TEXT_DIM), subLp);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(Ui.px(this, 12));
        header.addView(titles, titleLp);
        return header;
    }

    private LinearLayout card() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 16), Ui.px(this, 14), Ui.px(this, 16), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF0D1524, Ui.dp(this, 18), 0xFF1B2740, Math.max(1, Ui.px(this, 1))));
        return card;
    }

    private TextView label(String text) {
        return Ui.bold(this, text, 11.5f, 0xFFA8B6C9);
    }

    private EditText input(String hint, int lines) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(14f);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(0xFF66758B);
        field.setBackground(Ui.round(0xFF0A1120, Ui.dp(this, 12), 0xFF223350, Math.max(1, Ui.px(this, 1))));
        field.setPadding(Ui.px(this, 12), Ui.px(this, 10), Ui.px(this, 12), Ui.px(this, 10));
        field.setMinLines(lines);
        field.setMaxLines(lines > 1 ? 7 : 1);
        field.setGravity(Gravity.TOP | Gravity.START);
        if (lines > 1) {
            field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return field;
    }

    private void add(LinearLayout col, View v, float topDp) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(this, topDp);
        col.addView(v, lp);
    }

    // ------------------------------------------------------------------ preview

    private void scheduleUpdate() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::updatePreview, 160);
    }

    private void updatePreview() {
        if (isFinishing() || contentInput == null) {
            return;
        }
        String content = contentInput.getText().toString().trim();
        String type = QrStore.detectType(content);
        typeChip.setText(content.isEmpty() ? "Texto" : QrStore.typeLabel(type));

        if (content.isEmpty()) {
            preview.setImageDrawable(null);
            previewHint.setVisibility(View.VISIBLE);
            previewHint.setText("Escribe algo y el código aparece aquí.");
            saveButton.setAlpha(0.55f);
            copyButton.setAlpha(0.55f);
            return;
        }

        Bitmap qr = QrRenderer.create(content, 620);
        if (qr == null) {
            preview.setImageDrawable(null);
            previewHint.setVisibility(View.VISIBLE);
            previewHint.setText("El contenido es demasiado largo para un QR. Acórtalo un poco.");
            saveButton.setAlpha(0.55f);
        } else {
            preview.setImageBitmap(qr);
            previewHint.setVisibility(View.GONE);
            saveButton.setAlpha(1f);
        }
        copyButton.setAlpha(1f);
    }

    private void save() {
        if (saved) {
            return;
        }
        String content = contentInput.getText().toString().trim();
        if (content.isEmpty()) {
            Actions.toast(this, "Escribe el contenido del QR");
            contentInput.requestFocus();
            return;
        }
        if (QrRenderer.create(content, 620) == null) {
            Actions.toast(this, "El contenido es demasiado largo");
            return;
        }
        String name = nameInput.getText().toString().trim();
        String type = QrStore.detectType(content);
        QrStore.Entry entry = QrStore.add(this, name, content, type);
        if (entry == null) {
            Actions.toast(this, "No se pudo guardar");
            return;
        }
        saved = true;
        Actions.toast(this, "Guardado en Mis códigos");
        startActivity(new Intent(this, GalleryActivity.class));
        finish();
    }

    private static final class Watcher implements TextWatcher {
        private final Runnable action;

        Watcher(Runnable action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            action.run();
        }
    }
}
