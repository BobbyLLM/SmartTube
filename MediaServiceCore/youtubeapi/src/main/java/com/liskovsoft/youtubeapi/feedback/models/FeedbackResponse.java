package com.liskovsoft.youtubeapi.feedback.models;

import com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath;
import com.liskovsoft.googlecommon.common.models.V2.TextItem;

import java.util.List;

public class FeedbackResponse {
    @JsonPath("$.feedbackResponses[0].isProcessed")
    private boolean mIsProcessed;

    @JsonPath("$.feedbackResponses[0].followUpDialog.dismissalFollowUpRenderer.reasons")
    private List<FeedbackReason> mReasons;

    @JsonPath("$.feedbackResponses[0].followUpDialog.dismissalFollowUpRenderer.dismissalReasonsPrompt")
    private TextItem mTitle;

    public boolean isProcessed() {
        return mIsProcessed;
    }

    public List<FeedbackReason> getReasons() {
        return mReasons;
    }

    public String getTitle() {
        return mTitle != null ? mTitle.toString() : null;
    }
}
