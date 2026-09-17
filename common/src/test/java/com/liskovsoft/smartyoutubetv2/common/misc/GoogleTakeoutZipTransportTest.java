package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class GoogleTakeoutZipTransportTest {
    @Test
    public void returnsOriginalZipBytesForImporter() throws Exception {
        byte[] zip = createZip();
        ByteArrayInputStream source = new ByteArrayInputStream(zip);

        try (java.io.InputStream selected = GoogleTakeoutZipTransport.open(source)) {
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] buffer = new byte[128];
            int count;
            while ((count = selected.read(buffer)) >= 0) {
                received.write(buffer, 0, count);
            }
            assertArrayEquals(zip, received.toByteArray());
        }
    }

    @Test
    public void rejectsArchiveChildOrNonZipInput() throws Exception {
        try {
            GoogleTakeoutZipTransport.open(new ByteArrayInputStream("playlists.csv".getBytes("UTF-8")));
            fail("Archive child was accepted as a ZIP");
        } catch (IOException expected) {
            // Expected transport-boundary rejection.
        }
    }

    private static byte[] createZip() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("Takeout/playlists.csv"));
            zip.write("Playlist ID,Playlist Title (Original)\n".getBytes("UTF-8"));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
