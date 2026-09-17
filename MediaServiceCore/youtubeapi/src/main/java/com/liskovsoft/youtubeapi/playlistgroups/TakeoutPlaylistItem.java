package com.liskovsoft.youtubeapi.playlistgroups;

/** Metadata-ready item used by the narrow Takeout-to-native playlist boundary. */
public final class TakeoutPlaylistItem {
    private final String videoId;
    private final String title;
    private final String iconUrl;
    private final String channelId;
    private final CharSequence subtitle;
    private final String badge;

    public TakeoutPlaylistItem(String videoId, String title, String iconUrl,
                               String channelId, CharSequence subtitle, String badge) {
        this.videoId = videoId;
        this.title = title;
        this.iconUrl = iconUrl;
        this.channelId = channelId;
        this.subtitle = subtitle;
        this.badge = badge;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getChannelId() {
        return channelId;
    }

    public CharSequence getSubtitle() {
        return subtitle;
    }

    public String getBadge() {
        return badge;
    }
}
