package com.grupovaledamantiqueira.nfckey;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NfcAdapter nfcAdapter;
    private TextView statusView;
    private TextView hceLogView;
    private TextView cardView;
    private TextView selectedDoorView;
    private Button armButton;
    private Button readCardButton;
    private Spinner roomSpinner;
    private boolean readerModeActive;
    private final List<DoorOption> doorOptions = new ArrayList<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        loadDoorOptions();
        setContentView(buildUi());
        restoreSelectedDoor();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        stopReaderMode();
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(24), p, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("NFC Key Lab", 30, true, Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = text(
                "POC para testar chave móvel nas fechaduras Be-Tech/SAGA antes de integrar ao bis_api.",
                16, false, Color.rgb(75, 85, 99));
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        root.addView(sectionTitle("1. Diagnóstico do aparelho"));
        statusView = text("", 15, false, Color.rgb(31, 41, 55));
        root.addView(card(statusView));

        root.addView(sectionTitle("2. Selecionar quarto do BIS"));
        TextView roomHelp = text(
                "A lista abaixo foi extraída do btlock57.mdb. O texto mostra o número do quarto e o valor interno salvo é o door_id de 6 dígitos usado pelo BIS.",
                15, false, Color.rgb(55, 65, 81));
        root.addView(card(roomHelp));

        roomSpinner = new Spinner(this);
        List<String> labels = new ArrayList<>();
        labels.add("Selecione o quarto...");
        for (DoorOption option : doorOptions) {
            labels.add("Quarto " + option.room);
        }
        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels);
        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roomSpinner.setAdapter(roomAdapter);
        roomSpinner.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(roomSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)));

        selectedDoorView = text("Nenhum quarto selecionado.", 14, false, Color.rgb(31, 41, 55));
        selectedDoorView.setTextIsSelectable(true);
        root.addView(card(selectedDoorView));

        roomSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position <= 0) return;
                DoorOption option = doorOptions.get(position - 1);
                String currentDoor = NfcKeyStore.selectedDoorId(MainActivity.this);
                if (!option.doorId.equals(currentDoor)) {
                    NfcKeyStore.setSelectedDoor(MainActivity.this, option.room, option.doorId);
                }
                refreshUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        root.addView(sectionTitle("3. Teste na fechadura"));
        TextView instructions = text(
                "Selecione o quarto, arme o HCE, mantenha a tela desbloqueada e aproxime o telefone da fechadura. " +
                        "Se a fechadura conversar por ISO-DEP/APDU e selecionar o AID do laboratório, o contador abaixo aumenta. " +
                        "O door_id selecionado fica associado à sessão de teste.",
                15, false, Color.rgb(55, 65, 81));
        root.addView(card(instructions));

        armButton = button("Armar teste HCE");
        armButton.setOnClickListener(v -> {
            NfcKeyStore.setArmed(this, !NfcKeyStore.isArmed(this));
            refreshUi();
        });
        root.addView(armButton);

        hceLogView = text("", 14, false, Color.rgb(31, 41, 55));
        hceLogView.setTextIsSelectable(true);
        root.addView(card(hceLogView));

        Button clearButton = button("Limpar contador HCE");
        clearButton.setOnClickListener(v -> {
            NfcKeyStore.clearLog(this);
            refreshUi();
        });
        root.addView(clearButton);

        root.addView(sectionTitle("4. Conferir cartão físico"));
        TextView cardHelp = text(
                "Este teste NÃO lê HPASS nem tenta copiar setores. Ele apenas mostra UID e tecnologias NFC anunciadas pelo cartão, " +
                        "para confirmarmos se o cartão real é MIFARE Classic.",
                15, false, Color.rgb(55, 65, 81));
        root.addView(card(cardHelp));

        readCardButton = button("Ler cartão físico por 12 s");
        readCardButton.setOnClickListener(v -> startReaderMode());
        root.addView(readCardButton);

        cardView = text("Nenhum cartão lido nesta sessão.", 14, false, Color.rgb(31, 41, 55));
        cardView.setTextIsSelectable(true);
        root.addView(card(cardView));

        Button settingsButton = button("Abrir configurações de NFC");
        settingsButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
        });
        root.addView(settingsButton);

        TextView security = text(
                "Segurança do POC: HPASS e chaves MIFARE permanecem no servidor/bis_api. Nenhum segredo real do hotel está embutido no APK.",
                13, false, Color.rgb(107, 114, 128));
        security.setPadding(0, dp(20), 0, 0);
        root.addView(security);

        return scroll;
    }

    private void loadDoorOptions() {
        doorOptions.clear();
        try (InputStream input = getAssets().open("doors.json")) {
            byte[] bytes = new byte[input.available()];
            int read = input.read(bytes);
            if (read <= 0) return;
            JSONArray array = new JSONArray(new String(bytes, 0, read, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                doorOptions.add(new DoorOption(
                        obj.getString("room"),
                        obj.getString("doorId"),
                        obj.optString("rawDoorName", "")));
            }
        } catch (Exception ex) {
            NfcKeyStore.recordEvent(this, "Falha ao carregar doors.json: " + ex.getClass().getSimpleName());
        }
    }

    private void restoreSelectedDoor() {
        if (roomSpinner == null) return;
        String selected = NfcKeyStore.selectedDoorId(this);
        if (selected.isEmpty()) return;
        for (int i = 0; i < doorOptions.size(); i++) {
            if (selected.equals(doorOptions.get(i).doorId)) {
                roomSpinner.setSelection(i + 1, false);
                return;
            }
        }
    }

    private void refreshUi() {
        boolean hasNfc = nfcAdapter != null;
        boolean nfcEnabled = hasNfc && nfcAdapter.isEnabled();
        boolean hasHce = getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);
        boolean armed = NfcKeyStore.isArmed(this);
        boolean hasDoor = NfcKeyStore.hasSelectedDoor(this);

        String status = "NFC no aparelho: " + yesNo(hasNfc) +
                "\nNFC ligado: " + yesNo(nfcEnabled) +
                "\nHCE (Host Card Emulation): " + yesNo(hasHce) +
                "\nAID de laboratório: F04E46434B455931" +
                "\nQuartos/portas carregados: " + doorOptions.size() +
                "\nEstado do teste: " + (armed ? "ARMADO" : "desarmado");
        statusView.setText(status);

        if (hasDoor) {
            selectedDoorView.setText(
                    "Quarto selecionado: " + NfcKeyStore.selectedRoom(this) +
                            "\nBIS door_id (value): " + NfcKeyStore.selectedDoorId(this));
        } else {
            selectedDoorView.setText("Nenhum quarto selecionado.");
        }

        armButton.setText(armed ? "Desarmar teste HCE" : "Armar teste HCE");
        armButton.setEnabled((armed || hasDoor) && hasHce && nfcEnabled);

        hceLogView.setText(String.format(Locale.US,
                "APDUs recebidas: %d\nÚltima APDU: %s\nÚltimo evento: %s",
                NfcKeyStore.apduCount(this),
                NfcKeyStore.lastApdu(this),
                NfcKeyStore.lastEvent(this)));

        readCardButton.setEnabled(hasNfc && nfcEnabled && !readerModeActive);
        readCardButton.setText(readerModeActive ? "Aguardando cartão…" : "Ler cartão físico por 12 s");
    }

    private void startReaderMode() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled() || readerModeActive) return;

        readerModeActive = true;
        cardView.setText("Aproxime agora o cartão físico da traseira do telefone…");
        refreshUi();

        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

        nfcAdapter.enableReaderMode(this, this::onTagDiscovered, flags, null);
        handler.postDelayed(() -> {
            if (readerModeActive) {
                stopReaderMode();
                cardView.setText("Tempo de leitura encerrado sem cartão detectado.");
                refreshUi();
            }
        }, 12_000);
    }

    private void stopReaderMode() {
        if (nfcAdapter != null && readerModeActive) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
        readerModeActive = false;
    }

    private void onTagDiscovered(Tag tag) {
        String uid = NfcKeyStore.toHex(tag.getId());
        String[] techs = tag.getTechList();
        boolean mifareClassic = Arrays.stream(techs)
                .anyMatch(t -> t.endsWith("MifareClassic"));

        StringBuilder sb = new StringBuilder();
        sb.append("UID apresentado pelo cartão: ").append(uid.isEmpty() ? "(vazio)" : uid).append('\n');
        sb.append("Tecnologias: ").append(String.join(", ", techs)).append('\n');
        sb.append("MIFARE Classic: ").append(mifareClassic ? "SIM" : "não anunciado pelo aparelho").append('\n');
        if (mifareClassic) {
            sb.append("Resultado: compatível com o que o bis_api atualmente grava (MIFARE Classic). ");
            sb.append("Isso não significa que o Android consiga emular esse cartão em HCE.");
        } else {
            sb.append("Resultado: confirme em outro aparelho NFC se necessário; alguns chipsets Android não expõem MIFARE Classic.");
        }

        runOnUiThread(() -> {
            cardView.setText(sb.toString());
            stopReaderMode();
            refreshUi();
        });
    }

    private LinearLayout card(TextView content) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackgroundColor(Color.WHITE);
        box.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(12));
        box.setLayoutParams(params);
        box.setElevation(dp(1));
        return box;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 19, true, Color.rgb(17, 24, 39));
        view.setPadding(0, dp(14), 0, dp(4));
        return view;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52));
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String yesNo(boolean value) {
        return value ? "SIM" : "NÃO";
    }

    private static final class DoorOption {
        final String room;
        final String doorId;
        final String rawDoorName;

        DoorOption(String room, String doorId, String rawDoorName) {
            this.room = room;
            this.doorId = doorId;
            this.rawDoorName = rawDoorName;
        }
    }
}
