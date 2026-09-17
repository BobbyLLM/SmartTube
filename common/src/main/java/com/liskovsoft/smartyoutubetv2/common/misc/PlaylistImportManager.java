package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.youtubeapi.playlistgroups.PlaylistGroupServiceImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports exactly one local playlist record into the currently active profile. */
public final class PlaylistImportManager {
    private static final String PAYLOAD_ENTRY =
            "data/org.smarttube.stable/Backup/files/yt_service_prefs/anonymous_playlist_group_data";

    private PlaylistImportManager() {
    }

    @FunctionalInterface
    interface ImportSink {
        boolean importData(String data);
    }

    public static boolean importZip(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.isFile()) return false;

        try (FileInputStream input = new FileInputStream(zipFile)) {
            ByteArrayOutputStream selected = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) selected.write(buffer, 0, count);
            return importBytes(selected.toByteArray(), PlaylistGroupServiceImpl::importData);
        }
    }

    static boolean importBytes(byte[] selected, ImportSink sink) throws IOException {
        if (selected == null || selected.length == 0 || sink == null) return false;

        if (isZip(selected)) {
            return importZipBytes(selected, sink);
        }

        return sink.importData(new String(selected, Charset.forName("UTF-8")));
    }

    private static boolean importZipBytes(byte[] selected, ImportSink sink) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new java.io.ByteArrayInputStream(selected))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (PAYLOAD_ENTRY.equals(entry.getName())) {
                    ByteArrayOutputStream payload = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) payload.write(buffer, 0, count);
                    return sink.importData(new String(payload.toByteArray(), Charset.forName("UTF-8")));
                }
            }
        }

        return false;
    }

    private static boolean isZip(byte[] selected) {
        return selected.length >= 4
                && selected[0] == 'P'
                && selected[1] == 'K'
                && ((selected[2] == 3 && selected[3] == 4)
                || (selected[2] == 5 && selected[3] == 6)
                || (selected[2] == 7 && selected[3] == 8));
    }
}
