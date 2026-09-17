package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.youtubeapi.playlistgroups.TakeoutPlaylist;
import com.liskovsoft.youtubeapi.playlistgroups.TakeoutPlaylistItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Parses Google Takeout playlist CSVs without touching playlist persistence. */
public final class GoogleTakeoutPlaylistImporter {
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TOTAL_CSV_BYTES = 64 * 1024 * 1024;
    private static final int METADATA_THREADS = 4;
    private static final long METADATA_TIMEOUT_SECONDS = 30;

    private GoogleTakeoutPlaylistImporter() {
    }

    public interface MetadataResolver {
        Metadata resolve(String videoId) throws Exception;
    }

    public static final class Metadata {
        public final String title;
        public final String iconUrl;
        public final String channelId;
        public final CharSequence subtitle;
        public final String badge;

        public Metadata(String title, String iconUrl, String channelId,
                        CharSequence subtitle, String badge) {
            this.title = title;
            this.iconUrl = iconUrl;
            this.channelId = channelId;
            this.subtitle = subtitle;
            this.badge = badge;
        }
    }

    public static final class Result {
        private final List<TakeoutPlaylist> playlists;
        private final int placementCount;
        private final int uniqueVideoCount;
        private final int resolvedMetadataCount;
        private final int unavailableCount;
        private final int transientFailureCount;

        private Result(List<TakeoutPlaylist> playlists, int placementCount, int uniqueVideoCount,
                       int resolvedMetadataCount, int unavailableCount, int transientFailureCount) {
            this.playlists = Collections.unmodifiableList(new ArrayList<>(playlists));
            this.placementCount = placementCount;
            this.uniqueVideoCount = uniqueVideoCount;
            this.resolvedMetadataCount = resolvedMetadataCount;
            this.unavailableCount = unavailableCount;
            this.transientFailureCount = transientFailureCount;
        }

        public List<TakeoutPlaylist> getPlaylists() {
            return playlists;
        }

        public int getPlaylistCount() {
            return playlists.size();
        }

        public int getPlacementCount() {
            return placementCount;
        }

        public int getUniqueVideoCount() {
            return uniqueVideoCount;
        }

        public int getResolvedMetadataCount() {
            return resolvedMetadataCount;
        }

        public int getUnavailableCount() {
            return unavailableCount;
        }

        public int getTransientFailureCount() {
            return transientFailureCount;
        }
    }

    /** Structural parse only; useful for validation and tests without network access. */
    public static Result parse(InputStream input) throws IOException {
        return build(parseArchive(input), null);
    }

    /** Parse, resolve unique metadata IDs, and return a complete native-ready import plan. */
    public static Result importStream(InputStream input, MetadataResolver resolver) throws IOException {
        if (resolver == null) throw new IllegalArgumentException("Metadata resolver is required");
        return build(parseArchive(input), resolver);
    }

    private static Result build(ParsedArchive archive, MetadataResolver resolver) throws IOException {
        Map<String, MetadataOutcome> metadata = resolver == null
                ? Collections.emptyMap() : resolveMetadata(archive.uniqueVideoIds, resolver);
        int resolved = 0;
        int unavailable = 0;
        int transientFailures = 0;
        for (MetadataOutcome outcome : metadata.values()) {
            if (outcome.metadata != null) resolved++;
            else if (outcome.permanentlyUnavailable) unavailable++;
            else transientFailures++;
        }

        if (resolver != null && resolved == 0 && transientFailures > 0 && !archive.uniqueVideoIds.isEmpty()) {
            throw new IOException("Metadata transport failed for every video");
        }

        List<TakeoutPlaylist> playlists = new ArrayList<>();
        for (ParsedPlaylist playlist : archive.playlists) {
            List<TakeoutPlaylistItem> items = new ArrayList<>();
            for (String videoId : playlist.videoIds) {
                MetadataOutcome outcome = metadata.get(videoId);
                Metadata item = outcome != null ? outcome.metadata : null;
                items.add(new TakeoutPlaylistItem(
                        videoId,
                        item != null ? item.title : null,
                        item != null ? item.iconUrl : null,
                        item != null ? item.channelId : null,
                        item != null ? item.subtitle : null,
                        item != null ? item.badge : null
                ));
            }
            playlists.add(new TakeoutPlaylist(playlist.id, playlist.title, items));
        }

        return new Result(playlists, archive.placementCount, archive.uniqueVideoIds.size(),
                resolved, unavailable, transientFailures);
    }

    private static Map<String, MetadataOutcome> resolveMetadata(Set<String> ids, MetadataResolver resolver) {
        ExecutorService executor = Executors.newFixedThreadPool(METADATA_THREADS);
        Map<String, Future<MetadataOutcome>> futures = new LinkedHashMap<>();
        try {
            for (String id : ids) {
                futures.put(id, executor.submit(() -> resolveWithRetry(id, resolver)));
            }

            Map<String, MetadataOutcome> result = new LinkedHashMap<>();
            for (Map.Entry<String, Future<MetadataOutcome>> entry : futures.entrySet()) {
                try {
                    result.put(entry.getKey(), entry.getValue().get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    result.put(entry.getKey(), MetadataOutcome.transientFailure());
                } catch (ExecutionException | TimeoutException ex) {
                    entry.getValue().cancel(true);
                    result.put(entry.getKey(), MetadataOutcome.transientFailure());
                }
            }
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private static MetadataOutcome resolveWithRetry(String id, MetadataResolver resolver) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Metadata metadata = resolver.resolve(id);
                return metadata == null ? MetadataOutcome.unavailable() : MetadataOutcome.resolved(metadata);
            } catch (Exception ignored) {
                // A bounded retry distinguishes transport failure from a valid null/unavailable result.
            }
        }
        return MetadataOutcome.transientFailure();
    }

    private static ParsedArchive parseArchive(InputStream input) throws IOException {
        if (input == null) throw new IOException("Takeout input is missing");

        Map<String, byte[]> csvEntries = new LinkedHashMap<>();
        int totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.contains("../") || name.startsWith("../") || name.startsWith("/")) {
                    throw new IOException("Unsafe ZIP entry: " + name);
                }
                if (!entry.isDirectory() && name.toLowerCase().endsWith(".csv")) {
                    if (csvEntries.containsKey(name)) throw new IOException("Duplicate ZIP entry: " + name);
                    byte[] bytes = readBounded(zip, MAX_ENTRY_BYTES);
                    totalBytes += bytes.length;
                    if (totalBytes > MAX_TOTAL_CSV_BYTES) throw new IOException("Takeout CSV data is too large");
                    csvEntries.put(name, bytes);
                }
            }
        }

        CsvTable master = null;
        String masterName = null;
        for (Map.Entry<String, byte[]> entry : csvEntries.entrySet()) {
            CsvTable candidate = parseCsv(entry.getValue());
            if (candidate.has("Playlist ID") && candidate.has("Playlist Title (Original)")) {
                if (master != null) throw new IOException("Multiple playlist master CSVs found");
                master = candidate;
                masterName = entry.getKey();
            }
        }
        if (master == null) throw new IOException("Google Takeout playlist master CSV not found");
        if (master.rows.isEmpty()) throw new IOException("Google Takeout playlist master CSV is empty");

        Map<String, CsvTable> childByTitle = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : csvEntries.entrySet()) {
            if (entry.getKey().equals(masterName)) continue;
            String leaf = leafName(entry.getKey());
            if (!leaf.endsWith("-videos.csv")) continue;
            String title = leaf.substring(0, leaf.length() - "-videos.csv".length());
            if (childByTitle.put(title, parseCsv(entry.getValue())) != null) {
                throw new IOException("Ambiguous playlist-video CSV mapping: " + title);
            }
            if (!childByTitle.get(title).has("Video ID")) {
                throw new IOException("Playlist-video CSV is missing Video ID: " + leaf);
            }
        }

        List<ParsedPlaylist> playlists = new ArrayList<>();
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        int placements = 0;
        for (Map<String, String> row : master.rows) {
            String sourceId = trim(row.get("Playlist ID"));
            String title = row.get("Playlist Title (Original)");
            if (sourceId == null || title == null || title.trim().isEmpty()) {
                throw new IOException("Playlist master row is missing identity or title");
            }
            CsvTable child = childByTitle.get(title);
            if (child == null) throw new IOException("Playlist-video CSV not found for title: " + title);

            List<String> ids = new ArrayList<>();
            for (Map<String, String> childRow : child.rows) {
                String videoId = trim(childRow.get("Video ID"));
                if (videoId == null || videoId.isEmpty()) throw new IOException("Empty Video ID in playlist: " + title);
                ids.add(videoId);
                uniqueIds.add(videoId);
                placements++;
            }
            playlists.add(new ParsedPlaylist("takeout_" + sourceId, title, ids));
        }

        return new ParsedArchive(playlists, uniqueIds, placements);
    }

    private static CsvTable parseCsv(byte[] bytes) throws IOException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(new ByteArrayInputStream(bytes), Charset.forName("UTF-8")), 1);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean sawData = false;
        int value;
        while ((value = reader.read()) != -1) {
            char c = (char) value;
            if (quoted) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else {
                        quoted = false;
                        if (next != -1) reader.unread(next);
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
                sawData = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                sawData = true;
            } else if (c == '\r' || c == '\n') {
                record.add(field.toString());
                field.setLength(0);
                if (c == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                if (!isBlankRecord(record)) records.add(record);
                record = new ArrayList<>();
                sawData = false;
            } else {
                field.append(c);
                sawData = true;
            }
        }
        if (quoted) throw new IOException("Unclosed quoted CSV field");
        if (sawData || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            if (!isBlankRecord(record)) records.add(record);
        }
        if (records.isEmpty()) throw new IOException("Empty CSV");

        List<String> headers = new ArrayList<>(records.get(0));
        if (!headers.isEmpty() && headers.get(0).startsWith("\uFEFF")) {
            headers.set(0, headers.get(0).substring(1));
        }
        Set<String> seen = new HashSet<>();
        for (String header : headers) {
            if (!seen.add(header)) throw new IOException("Duplicate CSV header: " + header);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> values = records.get(i);
            if (values.size() > headers.size()) throw new IOException("CSV row has too many fields");
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < values.size() ? values.get(j) : "");
            }
            rows.add(row);
        }
        return new CsvTable(headers, rows);
    }

    private static boolean isBlankRecord(List<String> fields) {
        if (fields == null || fields.isEmpty()) return true;
        for (String field : fields) {
            if (field != null && !field.trim().isEmpty()) return false;
        }
        return true;
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("ZIP entry is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String leafName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private static final class CsvTable {
        private final List<String> headers;
        private final List<Map<String, String>> rows;

        private CsvTable(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }

        private boolean has(String header) {
            return headers.contains(header);
        }
    }

    private static final class ParsedPlaylist {
        private final String id;
        private final String title;
        private final List<String> videoIds;

        private ParsedPlaylist(String id, String title, List<String> videoIds) {
            this.id = id;
            this.title = title;
            this.videoIds = videoIds;
        }
    }

    private static final class ParsedArchive {
        private final List<ParsedPlaylist> playlists;
        private final Set<String> uniqueVideoIds;
        private final int placementCount;

        private ParsedArchive(List<ParsedPlaylist> playlists, Set<String> uniqueVideoIds, int placementCount) {
            this.playlists = playlists;
            this.uniqueVideoIds = uniqueVideoIds;
            this.placementCount = placementCount;
        }
    }

    private static final class MetadataOutcome {
        private final Metadata metadata;
        private final boolean permanentlyUnavailable;

        private MetadataOutcome(Metadata metadata, boolean permanentlyUnavailable) {
            this.metadata = metadata;
            this.permanentlyUnavailable = permanentlyUnavailable;
        }

        private static MetadataOutcome resolved(Metadata metadata) {
            return new MetadataOutcome(metadata, false);
        }

        private static MetadataOutcome unavailable() {
            return new MetadataOutcome(null, true);
        }

        private static MetadataOutcome transientFailure() {
            return new MetadataOutcome(null, false);
        }
    }
}
