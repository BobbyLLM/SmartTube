package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.List;

public interface FeedbackReasons {
    String getTitle();
    List<FeedbackItem> getItems();

    interface FeedbackItem {
        String getTitle();
        String getToken();
    }
}
