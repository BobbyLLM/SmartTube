package com.liskovsoft.youtubeapi.channelgroups

import com.liskovsoft.youtubeapi.service.internal.LocalProfileManager
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2Wrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelGroupServiceImplTest {
    @Test
    fun localMembershipIsProfileScopedDurableAndMetadataIndependent() {
        val profiles = LocalProfileManager.instance()
        val original = profiles.getActiveId()
        val profileA = profiles.create("subscription-test-a")
        val profileB = profiles.create("subscription-test-b")

        try {
            profiles.select(profileA.id)
            ChannelGroupServiceImpl.cachedChannel = null
            ChannelGroupServiceImpl.subscribe(false, "channel_a", null, null)
            ChannelGroupServiceImpl.subscribe(false, "channel_b", null, null)

            ChannelGroupServiceImpl.subscribe(true, "channel_a", null, null)
            ChannelGroupServiceImpl.subscribe(true, "channel_a", null, null)
            ChannelGroupServiceImpl.subscribe(true, "channel_b", "Channel B", null)

            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_a"))
            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_b"))
            assertEquals(listOf("channel_b", "channel_a"),
                ChannelGroupServiceImpl.getSubscribedChannelIds()?.toList())
            assertEquals(listOf("channel_b", "channel_a"),
                BrowseService2Wrapper.getSubscribedChannels()?.mediaItems?.mapNotNull { it.channelId })

            profiles.select(profileB.id)
            ChannelGroupServiceImpl.subscribe(true, "channel_c", null, null)
            assertEquals(listOf("channel_c"), ChannelGroupServiceImpl.getSubscribedChannelIds()?.toList())

            profiles.select(profileA.id)
            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_a"))
            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_b"))
            assertFalse(ChannelGroupServiceImpl.isSubscribed("channel_c"))

            ChannelGroupServiceImpl.subscribe(false, "channel_a", null, null)
            assertFalse(ChannelGroupServiceImpl.isSubscribed("channel_a"))
            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_b"))

            profiles.select(profileA.id)
            assertFalse(ChannelGroupServiceImpl.isSubscribed("channel_a"))
            assertTrue(ChannelGroupServiceImpl.isSubscribed("channel_b"))
        } finally {
            profiles.select(original)
            profiles.delete(profileB.id)
            profiles.delete(profileA.id)
        }
    }
}
