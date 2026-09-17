package com.liskovsoft.smartyoutubetv2.common.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class PlaylistImportManagerTest {
    private static final String PAYLOAD = "WL&sgi;Watch Later&sgi;null&sgi;group";
    private String imported;

    private final PlaylistImportManager.ImportSink sink = data -> {
        if (data == null || !data.startsWith("WL&sgi;")) return false;
        imported = data;
        return true;
    };

    @Test
    public void acceptsRawPayloadAndRoutesToSink() throws Exception {
        assertTrue(PlaylistImportManager.importBytes(bytes(PAYLOAD), sink));
        assertEquals(PAYLOAD, imported);
    }

    @Test
    public void acceptsZipPayloadAndRoutesToSameSink() throws Exception {
        assertTrue(PlaylistImportManager.importBytes(zip(PAYLOAD), sink));
        assertEquals(PAYLOAD, imported);
    }

    @Test
    public void rejectsEmptyMalformedAndUnrelatedInputs() throws Exception {
        assertFalse(PlaylistImportManager.importBytes(new byte[0], sink));
        assertFalse(PlaylistImportManager.importBytes(bytes("not a playlist"), sink));
        assertFalse(PlaylistImportManager.importBytes(zip("not a playlist"), sink));
        assertFalse(PlaylistImportManager.importBytes(zipWithoutPayload(), sink));
    }

    @Test
    public void reimportReplacesRatherThanAppends() throws Exception {
        assertTrue(PlaylistImportManager.importBytes(bytes(PAYLOAD), sink));
        String replacement = "WL&sgi;Watch Later&sgi;null&sgi;replacement";
        assertTrue(PlaylistImportManager.importBytes(bytes(replacement), sink));
        assertEquals(replacement, imported);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(Charset.forName("UTF-8"));
    }

    private static byte[] zip(String payload) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        zip.putNextEntry(new ZipEntry(
                "data/org.smarttube.stable/Backup/files/yt_service_prefs/anonymous_playlist_group_data"));
        zip.write(bytes(payload));
        zip.closeEntry();
        zip.close();
        return output.toByteArray();
    }

    private static byte[] zipWithoutPayload() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        zip.putNextEntry(new ZipEntry("unrelated.txt"));
        zip.write(bytes("unrelated"));
        zip.closeEntry();
        zip.close();
        return output.toByteArray();
    }
}
