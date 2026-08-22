package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.identity.LocalIdentity;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfficialIdentityImporterTest {
    @Test
    public void readsIdentityExportFromOfficialRecord() throws Exception {
        LocalIdentity expected = LocalIdentity.generateNew(8);
        String uuid = "c215e27d-5b86-f660-0b7b-53387fa0f4cb";
        String identityExport = expected.export().split("identity=\"")[1].split("\"")[0];
        byte[] nestedIdentity = field(1, identityExport.getBytes(StandardCharsets.UTF_8));
        byte[] record = concat(
                field(2, uuid.getBytes(StandardCharsets.UTF_8)),
                field(17, nestedIdentity));

        LocalIdentity restored = OfficialIdentityImporter.readIdentityRecord(record, uuid);

        assertEquals(expected.getUid().toString(), restored.getUid().toString());
        assertEquals(expected.getSecurityLevel(), restored.getSecurityLevel());
    }

    @Test
    public void ignoresARecordForAnotherOfficialIdentity() throws Exception {
        LocalIdentity identity = LocalIdentity.generateNew(8);
        String identityExport = identity.export().split("identity=\"")[1].split("\"")[0];
        byte[] record = concat(
                field(2, "different".getBytes(StandardCharsets.UTF_8)),
                field(17, field(1, identityExport.getBytes(StandardCharsets.UTF_8))));

        assertNull(OfficialIdentityImporter.readIdentityRecord(record, "preferred"));
    }

    private static byte[] field(int number, byte[] value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeVarint(output, (number << 3) | 2);
        writeVarint(output, value.length);
        output.write(value);
        return output.toByteArray();
    }

    private static void writeVarint(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7f) != 0) {
            output.write((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static byte[] concat(byte[]... values) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) output.write(value);
        return output.toByteArray();
    }
}
