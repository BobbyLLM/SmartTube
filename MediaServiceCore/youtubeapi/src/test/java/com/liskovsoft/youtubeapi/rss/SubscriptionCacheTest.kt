package com.liskovsoft.youtubeapi.rss

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.youtubeapi.channelgroups.ChannelGroupServiceImpl
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import com.liskovsoft.youtubeapi.service.internal.LocalProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriptionCacheTest {
    @Test
    fun staleCacheEmitsImmediatelyAndRetainsLastGoodResultOnFailure() {
        withProfile("subscription-cache-stale") { channelId ->
            var now = 1_000_000L
            var calls = 0
            RssService.nowProvider = { now }
            RssService.feedLoader = {
                calls++
                listOf(video(channelId, "video-$calls", now))
            }
            subscribe(channelId)

            val first = collect()
            assertEquals(1, calls)
            assertEquals(1, first.single().mediaItems?.size)

            RssService.reloadForTests()
            assertEquals(1, collect().size)
            assertEquals(1, calls)

            now += 15 * 60 * 1_000L + 1
            val staleThenFresh = collect()
            assertEquals(2, staleThenFresh.size)
            assertEquals("video-1", staleThenFresh.first().mediaItems?.first()?.videoId)
            assertEquals("video-2", staleThenFresh.last().mediaItems?.first()?.videoId)

            now += 15 * 60 * 1_000L + 1
            RssService.feedLoader = { null }
            val failedRefresh = collect()
            assertEquals(3, calls)
            assertEquals(1, failedRefresh.size)
            assertEquals("video-2", failedRefresh.single().mediaItems?.first()?.videoId)
        }
    }

    @Test
    fun freshCacheAvoidsNetworkAndManualRefreshBypassesTtl() {
        withProfile("subscription-cache-force") { channelId ->
            var now = 2_000_000L
            var calls = 0
            RssService.nowProvider = { now }
            RssService.feedLoader = {
                calls++
                listOf(video(channelId, "video-$calls", now))
            }
            subscribe(channelId)
            collect()

            assertEquals(1, collect().size)
            assertEquals(1, calls)

            RssService.requestSubscriptionRefresh()
            val forced = collect()
            assertEquals(2, calls)
            assertEquals(2, forced.size)
            assertEquals("video-2", forced.last().mediaItems?.first()?.videoId)
        }
    }

    @Test
    fun moreThan100ChannelsRemainEligibleAndMergedFeedIsCappedAndDeduplicated() {
        withProfile("subscription-cache-cap") { firstChannel ->
            val channels = (0..100).map { if (it == 0) firstChannel else "channel-$it" }
            channels.drop(1).forEach { ChannelGroupServiceImpl.subscribe(true, it, "Title $it", "icon-$it") }
            var calls = 0
            RssService.feedLoader = { channelId ->
                calls++
                if (channelId == firstChannel) {
                    (0..300).map { video(channelId, if (it == 300) "video-0" else "video-$it", it.toLong()) }
                } else listOf(video(channelId, "single-$channelId", calls.toLong()))
            }
            val result = collect().single()
            assertEquals(101, calls)
            assertEquals(300, result.mediaItems?.size)
            assertEquals(300, result.mediaItems?.mapNotNull { it.videoId }?.distinct()?.size)
            assertTrue(result.mediaItems?.first()?.publishedDate ?: 0L >= result.mediaItems?.last()?.publishedDate ?: 0L)
        }
    }

    @Test
    fun cacheIsProfileScopedAndUnsubscribeRemovesChannelFromMerge() {
        val profiles = LocalProfileManager.instance()
        val original = profiles.activeId
        val profileA = profiles.create("subscription-cache-a")
        val profileB = profiles.create("subscription-cache-b")
        try {
            RssService.resetForTests()
            profiles.select(profileA.id)
            subscribe("profile-a-channel")
            RssService.feedLoader = { listOf(video("profile-a-channel", "a-video", 1)) }
            assertEquals("a-video", collect().single().mediaItems?.first()?.videoId)

            ChannelGroupServiceImpl.subscribe(false, "profile-a-channel", null, null)
            assertTrue(collect().isEmpty())

            profiles.select(profileB.id)
            subscribe("profile-b-channel")
            RssService.feedLoader = { listOf(video("profile-b-channel", "b-video", 1)) }
            assertEquals("b-video", collect().single().mediaItems?.first()?.videoId)
        } finally {
            profiles.select(original)
            profiles.delete(profileB.id)
            profiles.delete(profileA.id)
            RssService.resetForTests()
        }
    }

    private fun collect(): List<MediaGroup> =
        RssService.getSubscriptionFeedObserve(MediaGroup.TYPE_SUBSCRIPTIONS).toList().blockingGet()

    private fun subscribe(channelId: String) {
        ChannelGroupServiceImpl.subscribe(true, channelId, "Title $channelId", "icon-$channelId")
    }

    private fun video(channelId: String, videoId: String, publishedDate: Long): MediaItem =
        YouTubeMediaItem().apply {
            this.channelId = channelId
            this.videoId = videoId
            this.title = videoId
            this.publishedDate = publishedDate
        }

    private fun withProfile(name: String, block: (String) -> Unit) {
        val profiles = LocalProfileManager.instance()
        val original = profiles.activeId
        val profile = profiles.create(name)
        try {
            RssService.resetForTests()
            block("channel-${profile.id}")
        } finally {
            profiles.select(original)
            profiles.delete(profile.id)
            RssService.resetForTests()
        }
    }
}
