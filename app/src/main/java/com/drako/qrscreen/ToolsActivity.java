package com.drako.qrscreen;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Herramientas: mi información, contactos para los que se crean QR y actualizaciones. */
public class ToolsActivity extends Activity {

    private LinearLayout contactsBox;
    private EditText ownerName;
    private EditText ownerEmail;
    private EditText ownerPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(build());
        loadOwner();
        renderContacts();
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0A1220, 0xFF070A12}));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.px(this, 18), Ui.px(this, 10), Ui.px(this, 18), Ui.px(this, 32));
        scroll.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // cabecera
        LinearLayout header = Ui.row(this);
        TextView back = Ui.bold(this, "‹", 22f, Ui.TEXT);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.ripple(Ui.round(0xFF14203A, Ui.dp(this, 12)), 0x33FFFFFF));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.px(this, 38), Ui.px(this, 38)));
        LinearLayout titles = Ui.column(this);
        titles.addView(Ui.bold(this, "Herramientas", 18f, Ui.TEXT));
        LinearLayout.LayoutParams subLp = Ui.matchWrap();
        subLp.topMargin = Ui.px(this, 1);
        titles.addView(Ui.text(this, "Tus datos, tus contactos y actualizaciones", 11.5f, Ui.TEXT_DIM), subLp);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.setMarginStart(Ui.px(this, 12));
        header.addView(titles, titlesLp);
        col.addView(header, Ui.matchWrap());

        // mi información
        LinearLayout ownerCard = card();
        ownerCard.addView(Ui.bold(this, "Mi información", 14f, Ui.TEXT));
        ownerCard.addView(Ui.text(this, "Se usa para crear tu tarjeta de contacto en un QR.",
                10.5f, Ui.TEXT_DIM), smallTop(6));
        ownerName = field("Nombre y apellidos");
        ownerEmail = field("Correo electrónico");
        ownerPhone = field("Teléfono");
        addField(ownerCard, "Nombre", ownerName, 12);
        addField(ownerCard, "Email", ownerEmail, 10);
        addField(ownerCard, "Teléfono", ownerPhone, 10);
        TextView save = Ui.button(this, "Guardar mi información", 13f);
        save.setOnClickListener(v -> {
            ProfileStore.setOwner(this, text(ownerName), text(ownerEmail), text(ownerPhone));
            Actions.toast(this, "Información guardada");
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.topMargin = Ui.px(this, 12);
        ownerCard.addView(save, saveLp);
        add(col, ownerCard, 16);

        // contactos
        LinearLayout contactsCard = card();
        contactsCard.addView(Ui.bold(this, "Contactos", 14f, Ui.TEXT));
        contactsCard.addView(Ui.text(this, "Personas para las que creas códigos. Al crear un QR puedes rellenarlo con sus datos.",
                10.5f, Ui.TEXT_DIM), smallTop(6));

        contactsBox = Ui.column(this);
        LinearLayout.LayoutParams boxLp = Ui.matchWrap();
        boxLp.topMargin = Ui.px(this, 10);
        contactsCard.addView(contactsBox, boxLp);

        LinearLayout addRow = Ui.column(this);
        LinearLayout.LayoutParams addRowLp = Ui.matchWrap();
        addRowLp.topMargin = Ui.px(this, 12);
        EditText newName = field("Nombre");
        EditText newEmail = field("Email");
        EditText newPhone = field("Teléfono (opcional)");
        addField(addRow, null, newName, 0);
        addField(addRow, null, newEmail, 8);
        addField(addRow, null, newPhone, 8);
        TextView addButton = Ui.button(this, "Añadir contacto", 13f);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.topMargin = Ui.px(this, 10);
        addButton.setOnClickListener(v -> {
            if (!ProfileStore.addContact(this, text(newName), text(newEmail), text(newPhone))) {
                Actions.toast(this, "Escribe al menos nombre, email o teléfono");
                return;
            }
            newName.setText("");
            newEmail.setText("");
            newPhone.setText("");
            renderContacts();
            Actions.toast(this, "Contacto añadido");
        });
        addRow.addView(addButton, addLp);
        contactsCard.addView(addRow, addRowLp);
        add(col, contactsCard, 10);

        // actualizaciones
        LinearLayout updateCard = card();
        updateCard.addView(Ui.bold(this, "Actualizaciones", 14f, Ui.TEXT));
        updateCard.addView(Ui.text(this, "Comprueba si hay una versión nueva en GitHub.",
                10.5f, Ui.TEXT_DIM), smallTop(6));
        TextView check = Ui.button(this, "Buscar actualizaciones", 13f);
        check.setOnClickListener(v -> Updater.check(this, true));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checkLp.topMargin = Ui.px(this, 12);
        updateCard.addView(check, checkLp);
        add(col, updateCard, 10);

        return root;
    }

    private void loadOwner() {
        ProfileStore.Person me = ProfileStore.owner(this);
        ownerName.setText(me.name);
        ownerEmail.setText(me.email);
        ownerPhone.setText(me.phone);
    }

    private void renderContacts() {
        if (contactsBox == null) {
            return;
        }
        contactsBox.removeAllViews();
        List<ProfileStore.Person> contacts = ProfileStore.contacts(this);
        if (contacts.isEmpty()) {
            TextView empty = Ui.text(this, "Todavía no hay contactos.", 12f, Ui.TEXT_DIM);
            contactsBox.addView(empty, Ui.matchWrap());
            return;
        }
        for (int i = 0; i < contacts.size(); i++) {
            final int index = i;
            final ProfileStore.Person person = contacts.get(i);
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.px(this, 10), Ui.px(this, 9), Ui.px(this, 10), Ui.px(this, 9));
            row.setBackground(Ui.round(0xFF0B1120, Ui.dp(this, 12),
                    0xFF1D2A42, Math.max(1, Ui.px(this, 1))));

            LinearLayout texts = Ui.column(this);
            texts.addView(Ui.bold(this, person.label(), 12.5f, Ui.TEXT));
            String details = ProfileStore.details(person);
            if (!details.isEmpty()) {
                TextView meta = Ui.text(this, details, 10.5f, Ui.TEXT_DIM);
                LinearLayout.LayoutParams metaLp = Ui.matchWrap();
                metaLp.topMargin = Ui.px(this, 1);
                texts.addView(meta, metaLp);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView remove = Ui.bold(this, "✕", 13f, Ui.DANGER);
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(Ui.ripple(Ui.round(0x00000000, Ui.dp(this, 14)), 0x33FFFFFF));
            remove.setOnClickListener(v -> {
                ProfileStore.removeContact(this, index);
                renderContacts();
            });
            row.addView(remove, new LinearLayout.LayoutParams(Ui.px(this, 30), Ui.px(this, 30)));

            LinearLayout.LayoutParams rowLp = Ui.matchWrap();
            rowLp.topMargin = Ui.px(this, 6);
            contactsBox.addView(row, rowLp);
        }
    }

    private LinearLayout card() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.px(this, 16), Ui.px(this, 14), Ui.px(this, 16), Ui.px(this, 14));
        card.setBackground(Ui.round(0xFF0D1524, Ui.dp(this, 18), 0xFF1B2740, Math.max(1, Ui.px(this, 1))));
        return card;
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(14f);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(0xFF66758B);
        input.setBackground(Ui.round(0xFF0A1120, Ui.dp(this, 12), 0xFF223350, Math.max(1, Ui.px(this, 1))));
        input.setPadding(Ui.px(this, 12), Ui.px(this, 10), Ui.px(this, 12), Ui.px(this, 10));
        input.setSingleLine(true);
        return input;
    }

    private void addField(LinearLayout parent, String label, EditText input, float topDp) {
        if (label != null) {
            TextView caption = Ui.text(this, label, 10.5f, Ui.TEXT_DIM);
            LinearLayout.LayoutParams captionLp = Ui.matchWrap();
            captionLp.topMargin = Ui.px(this, topDp > 0 ? topDp : 8);
            parent.addView(caption, captionLp);
        }
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(this, label == null ? topDp : 4);
        parent.addView(input, lp);
    }

    private LinearLayout.LayoutParams smallTop(float dp) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(this, dp);
        return lp;
    }

    private void add(LinearLayout col, View v, float topDp) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.px(this, topDp);
        col.addView(v, lp);
    }

    private String text(EditText input) {
        return input.getText().toString().trim();
    }
}
