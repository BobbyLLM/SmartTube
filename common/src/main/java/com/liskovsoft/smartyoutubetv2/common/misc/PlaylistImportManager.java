package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.youtubeapi.playlistgroups.PlaylistGroupServiceImpl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Imports exactly one local playlist record into the currently active profile. */
public final class PlaylistImportManager {
    private static final String PAYLOAD_ENTRY =
            "data/org.smarttube.stable/Backup/files/yt_service_prefs/anonymous_playlist_group_data";

    private PlaylistImportManager() {
    }

    public static boolean importZip(File zipFile) throws IOException {
        if (zipFile == null || !zipFile.isFile()) return false;

        try (ZipInputStream input = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (PAYLOAD_ENTRY.equals(entry.getName())) {
                    ByteArrayOutputStream payload = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) payload.write(buffer, 0, count);
                    return PlaylistGroupServiceImpl.importData(
                            new String(payload.toByteArray(), StandardCharsets.UTF_8));
                }
            }
        }

        return false;
    }
}
