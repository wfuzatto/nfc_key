package com.grupovaledamantiqueira.nfckey;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.DateFormat;
import java.util.Date;

final class NfcKeyStore {
    static final String PREFS = "nfc_key_lab";
    static final String KEY_ARMED = "armed";
    static final String KEY_APDU_COUNT = "apdu_count";
    static final String KEY_LAST_APDU = "last_apdu";
    static final String KEY_LAST_EVENT = "last_event";
    static final String KEY_ROOM = "selected_room";
    static final String KEY_DOOR_ID = "selected_door_id";

    private NfcKeyStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isArmed(Context context) {
        return prefs(context).getBoolean(KEY_ARMED, false);
    }

    static void setArmed(Context context, boolean armed) {
        prefs(context).edit()
                .putBoolean(KEY_ARMED, armed)
                .putString(KEY_LAST_EVENT, timestamp() + " | " + (armed ? "Teste HCE armado" : "Teste HCE desarmado"))
                .apply();
    }

    static void setSelectedDoor(Context context, String room, String doorId) {
        prefs(context).edit()
                .putString(KEY_ROOM, room == null ? "" : room)
                .putString(KEY_DOOR_ID, doorId == null ? "" : doorId)
                .putString(KEY_LAST_EVENT, timestamp() + " | Porta selecionada: quarto " + room + " / " + doorId)
                .apply();
    }

    static String selectedRoom(Context context) {
        return prefs(context).getString(KEY_ROOM, "");
    }

    static String selectedDoorId(Context context) {
        return prefs(context).getString(KEY_DOOR_ID, "");
    }

    static boolean hasSelectedDoor(Context context) {
        return !selectedRoom(context).isEmpty() && !selectedDoorId(context).isEmpty();
    }

    static void recordApdu(Context context, byte[] apdu) {
        SharedPreferences p = prefs(context);
        int count = p.getInt(KEY_APDU_COUNT, 0) + 1;
        p.edit()
                .putInt(KEY_APDU_COUNT, count)
                .putString(KEY_LAST_APDU, toHex(apdu))
                .putString(KEY_LAST_EVENT, timestamp() + " | APDU recebida (#" + count + ")")
                .apply();
    }

    static void recordEvent(Context context, String event) {
        prefs(context).edit()
                .putString(KEY_LAST_EVENT, timestamp() + " | " + event)
                .apply();
    }

    static int apduCount(Context context) {
        return prefs(context).getInt(KEY_APDU_COUNT, 0);
    }

    static String lastApdu(Context context) {
        return prefs(context).getString(KEY_LAST_APDU, "—");
    }

    static String lastEvent(Context context) {
        return prefs(context).getString(KEY_LAST_EVENT, "Nenhum evento HCE ainda.");
    }

    static void clearLog(Context context) {
        prefs(context).edit()
                .putInt(KEY_APDU_COUNT, 0)
                .remove(KEY_LAST_APDU)
                .putString(KEY_LAST_EVENT, timestamp() + " | Log limpo")
                .apply();
    }

    static String toHex(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static String timestamp() {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date());
    }
}
