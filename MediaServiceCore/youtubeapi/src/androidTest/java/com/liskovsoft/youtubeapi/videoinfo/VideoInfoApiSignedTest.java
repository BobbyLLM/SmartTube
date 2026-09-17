package com.liskovsoft.youtubeapi.videoinfo;

import static org.junit.Assert.assertTrue;

import com.liskovsoft.googlecommon.common.helpers.RetrofitOkHttpHelper;
import com.liskovsoft.googlecommon.common.helpers.tests.TestHelpers;
import com.liskovsoft.youtubeapi.common.helpers.AppClient;
import com.liskovsoft.youtubeapi.innertube.initialresponse.InitialResponseService;
import com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * NOTE: testing with Duktape (native libs)!!!
 */
public class VideoInfoApiSignedTest extends BaseVideoInfoApiTest {
    @Before
    public void setUp() {
        initBase();
        RetrofitOkHttpHelper.setDisableCompression(true);
        RetrofitOkHttpHelper.getAuthHeaders().put("Authorization", TestHelpers.getAuthorization());
        RetrofitOkHttpHelper.getAuthHeaders().put("X-Goog-Pageid", TestHelpers.getPageIdToken());
    }

    @Test
    public void testThatAgeRestrictedVideoContainsRequiredFields() {
        testThatNonLiveVideoInfoContainsRequiredFields(getVideoInfo(TestHelpers.VIDEO_ID_AGE_RESTRICTED));
    }

    @Test
    public void testThatUnavailableVideoContainsRequiredFields() {
        testThatNonLiveVideoInfoContainsRequiredFields(getVideoInfo(TestHelpers.VIDEO_ID_UNAVAILABLE));
    }

    @Test
    public void testThatLiveVideoContainsSpecificFields()  {
        testThatLiveVideoContainsSpecificFields(getVideoInfo(TestHelpers.VIDEO_ID_LIVE));
    }

    @Test
    public void testThatVideoWithCaptionsContainsRequiredFields() {
        testThatVideoWithCaptionsContainsRequiredFields(getVideoInfo(TestHelpers.VIDEO_ID_CAPTIONS));
    }

    @Ignore("Protected by CAPTCHA")
    @Test
    public void initialResponseTest() {
        testThatVideoInfoContainsRequiredFields(InitialResponseService.getVideoInfo(TestHelpers.VIDEO_ID_MUSIC_2, true));
    }

    @Test
    public void testThatClientsHaveNonEmptyResponses() {
        List<String> workingClients = new ArrayList<>();
        for (AppClient client : AppClient.values()) {
            //if (client.isPlaybackBroken()) {
            //    continue;
            //}

            VideoInfo result = getVideoInfo(client, TestHelpers.VIDEO_ID_CARTOON);
            //assertNotNull("Result not null for client " + client.name(), result);
            //assertFalse("Result is playable for client " + client.name(), result.isUnplayable());
            if (result != null && !result.isUnplayable()) {
                testThatVideoInfoContainsRequiredFields(result);
                workingClients.add(client.name());
            }
        }

        // workingClients = {java.util.ArrayList@28038}  size = 8
        // 0 = "TV"
        // 1 = "TV_LEGACY"
        // 2 = "TV_SIMPLY"
        // 3 = "TV_KIDS"
        // 4 = "TV_DOWNGRADED"
        // 5 = "ANDROID"
        // 6 = "ANDROID_SDK_LESS"
        // 7 = "ANDROID_REEL"
        assertTrue("Has at least 3 working clients", workingClients.size() >= 3);
    }
}