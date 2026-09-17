package com.liskovsoft.youtubeapi.feedback.models;

import com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath;
import com.liskovsoft.googlecommon.common.models.V2.TextItem;

public class FeedbackReason {
    @JsonPath("$.dismissalReasonTextRenderer.text")
    private TextItem mTitle;

    @JsonPath("$.dismissalReasonTextRenderer.feedback.feedbackEndpoint.feedbackToken")
    private String mToken;

    public String getTitle() {
        return mTitle != null ? mTitle.toString() : null;
    }

    public String getToken() {
        return mToken;
    }
}
