package com.liskovsoft.youtubeapi.playlistgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public import boundary for native local playlist construction. */
public final class TakeoutPlaylist {
    private final String id;
    private final String title;
    private final List<TakeoutPlaylistItem> items;

    public TakeoutPlaylist(String id, String title, List<TakeoutPlaylistItem> items) {
        this.id = id;
        this.title = title;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<TakeoutPlaylistItem> getItems() {
        return items;
    }
}
