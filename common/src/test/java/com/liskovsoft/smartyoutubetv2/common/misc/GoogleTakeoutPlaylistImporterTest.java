package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GoogleTakeoutPlaylistImporterTest {
    @Test
    public void parsesTakeoutCsvAndPreservesOrderDuplicatesAndWhitespace() throws Exception {
        GoogleTakeoutPlaylistImporter.Result result = GoogleTakeoutPlaylistImporter.parse(
                new ByteArrayInputStream(createArchive(false)));
        assertEquals(2, result.getPlaylistCount());
        assertEquals(4, result.getPlacementCount());
        assertEquals(3, result.getUniqueVideoCount());
        assertEquals("takeout_PL1", result.getPlaylists().get(0).getId());
        assertEquals("Comma, \"Title\"", result.getPlaylists().get(0).getTitle());
        assertEquals("v1", result.getPlaylists().get(0).getItems().get(0).getVideoId());
        assertEquals("missing", result.getPlaylists().get(0).getItems().get(1).getVideoId());
        assertEquals("v1", result.getPlaylists().get(0).getItems().get(2).getVideoId());
        assertEquals("v2", result.getPlaylists().get(1).getItems().get(0).getVideoId());
    }

    @Test
    public void resolvesEachUniqueVideoOnceAndRetainsUnavailablePlacement() throws Exception {
        Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        GoogleTakeoutPlaylistImporter.Result result = GoogleTakeoutPlaylistImporter.importStream(
                new ByteArrayInputStream(createArchive(false)), videoId -> {
                    calls.computeIfAbsent(videoId, ignored -> new AtomicInteger()).incrementAndGet();
                    if ("missing".equals(videoId)) return null;
                    return new GoogleTakeoutPlaylistImporter.Metadata(
                            "Title " + videoId, "icon-" + videoId, "channel", "subtitle", "badge");
                });
        assertEquals(1, calls.get("v1").get());
        assertEquals(1, calls.get("v2").get());
        assertEquals(1, calls.get("missing").get());
        assertEquals(2, result.getResolvedMetadataCount());
        assertEquals(1, result.getUnavailableCount());
        assertEquals("Title v1", result.getPlaylists().get(0).getItems().get(0).getTitle());
        assertEquals(null, result.getPlaylists().get(0).getItems().get(1).getTitle());
        assertEquals("v1", result.getPlaylists().get(0).getItems().get(2).getVideoId());
    }

    @Test
    public void rejectsMalformedCsvBeforeImportPlanExists() {
        try {
            GoogleTakeoutPlaylistImporter.parse(new ByteArrayInputStream(createArchive(true)));
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Malformed CSV was accepted");
    }

    @Test
    public void ignoresExactlyFiveTrailingBlankRows() throws Exception {
        GoogleTakeoutPlaylistImporter.Result result = GoogleTakeoutPlaylistImporter.parse(
                new ByteArrayInputStream(createSinglePlaylistArchive("Video ID", "v1\r\n\r\n\r\n\r\n\r\n\r\n")));
        assertEquals(1, result.getPlacementCount());
        assertEquals("v1", result.getPlaylists().get(0).getItems().get(0).getVideoId());
    }

    @Test
    public void ignoresBlankRowsBetweenValidRowsAndPreservesDuplicates() throws Exception {
        GoogleTakeoutPlaylistImporter.Result result = GoogleTakeoutPlaylistImporter.parse(
                new ByteArrayInputStream(createSinglePlaylistArchive("Video ID", "a\n\n   \n b \n a\n")));
        assertEquals(3, result.getPlacementCount());
        assertEquals("a", result.getPlaylists().get(0).getItems().get(0).getVideoId());
        assertEquals("b", result.getPlaylists().get(0).getItems().get(1).getVideoId());
        assertEquals("a", result.getPlaylists().get(0).getItems().get(2).getVideoId());
    }

    @Test
    public void rejectsNonBlankRowWithEmptyVideoId() throws Exception {
        try {
            GoogleTakeoutPlaylistImporter.parse(new ByteArrayInputStream(
                    createSinglePlaylistArchive("Video ID,Title", ",populated title\r\n")));
            fail("Non-blank row with empty Video ID was accepted");
        } catch (Exception expected) {
            assertEquals("Empty Video ID in playlist: HomeLab", expected.getMessage());
        }
    }

    private static byte[] createArchive(boolean malformed) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            String root = "Takeout/YouTube and YouTube Music/playlists/";
            String master = "\uFEFFPlaylist ID,Playlist Title (Original)\r\n"
                    + "PL1,\"Comma, \"\"Title\"\"\"\r\n"
                    + "PL2,Second\r\n";
            if (malformed) master = "Playlist ID,Playlist Title (Original)\r\nPL1,\"unclosed\r\n";
            put(zip, root + "playlists.csv", master);
            put(zip, root + "Comma, \"Title\"-videos.csv",
                    "Video ID\r\n v1 \r\nmissing\r\nv1\r\n");
            put(zip, root + "Second-videos.csv", "Video ID\n v2\n");
        }
        return bytes.toByteArray();
    }

    private static byte[] createSinglePlaylistArchive(String childHeader, String childRows) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            String root = "Takeout/YouTube and YouTube Music/playlists/";
            put(zip, root + "playlists.csv",
                    "\uFEFFPlaylist ID,Playlist Title (Original)\r\nPL1,HomeLab\r\n");
            put(zip, root + "HomeLab-videos.csv", childHeader + "\r\n" + childRows);
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
