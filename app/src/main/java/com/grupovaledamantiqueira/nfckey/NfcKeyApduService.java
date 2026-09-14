package com.grupovaledamantiqueira.nfckey;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NfcKeyApduService extends HostApduService {
    private static final byte[] LAB_AID = hex("F04E46434B455931");
    private static final byte[] SW_OK = hex("9000");
    private static final byte[] SW_CONDITIONS_NOT_SATISFIED = hex("6985");
    private static final byte[] SW_INS_NOT_SUPPORTED = hex("6D00");
    private static final byte[] SW_WRONG_DATA = hex("6A80");

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        NfcKeyStore.recordApdu(this, commandApdu);

        if (!NfcKeyStore.isArmed(this) || !NfcKeyStore.hasSelectedDoor(this)) {
            return SW_CONDITIONS_NOT_SATISFIED;
        }

        String room = NfcKeyStore.selectedRoom(this);
        String doorId = NfcKeyStore.selectedDoorId(this);

        if (isSelectOurAid(commandApdu)) {
            NfcKeyStore.recordEvent(this,
                    "SELECT AID recebido; quarto " + room + " / door_id " + doorId);
            return SW_OK;
        }

        // Comando proprietário apenas para teste com outro leitor/app de laboratório.
        // 80 CA 00 00 00 -> retorna versão, quarto e door_id selecionados + 90 00.
        if (commandApdu != null && commandApdu.length >= 5
                && (commandApdu[0] & 0xFF) == 0x80
                && (commandApdu[1] & 0xFF) == 0xCA
                && commandApdu[2] == 0x00
                && commandApdu[3] == 0x00) {
            String state = "NFC_KEY_LAB/0.2;ROOM=" + room + ";DOOR=" + doorId + ";ARMED=1";
            byte[] payload = state.getBytes(StandardCharsets.UTF_8);
            return concat(payload, SW_OK);
        }

        if (commandApdu == null || commandApdu.length < 4) {
            return SW_WRONG_DATA;
        }

        return SW_INS_NOT_SUPPORTED;
    }

    @Override
    public void onDeactivated(int reason) {
        String why = reason == DEACTIVATION_LINK_LOSS ? "link NFC perdido" : "AID desselecionado";
        NfcKeyStore.recordEvent(this, "Sessão HCE encerrada: " + why);
    }

    private static boolean isSelectOurAid(byte[] apdu) {
        if (apdu == null || apdu.length < 5 + LAB_AID.length) return false;
        if ((apdu[0] & 0xFF) != 0x00 || (apdu[1] & 0xFF) != 0xA4
                || (apdu[2] & 0xFF) != 0x04 || (apdu[3] & 0xFF) != 0x00) {
            return false;
        }
        int lc = apdu[4] & 0xFF;
        if (lc != LAB_AID.length || apdu.length < 5 + lc) return false;
        return Arrays.equals(LAB_AID, Arrays.copyOfRange(apdu, 5, 5 + lc));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] hex(String value) {
        int length = value.length();
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return result;
    }
}
