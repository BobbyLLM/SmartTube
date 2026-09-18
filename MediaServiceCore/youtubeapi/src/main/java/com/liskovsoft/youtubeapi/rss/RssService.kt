package com.liskovsoft.youtubeapi.rss

import com.google.gson.Gson
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.helpers.Helpers
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.app.nsigsolver.common.YouTubeInfoExtractor
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2Wrapper
import com.liskovsoft.youtubeapi.channelgroups.ChannelGroupServiceImpl
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaGroup
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import com.liskovsoft.youtubeapi.service.internal.LocalProfileManager
import com.liskovsoft.youtubeapi.service.internal.MediaServicePrefs
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal object RssService {
    private const val RSS_URL: String = "https://www.youtube.com/feeds/videos.xml?channel_id="
    private const val CACHE_KEY = "subscription_feed_cache_v1"
    private const val SOFT_TTL_MS = 15 * 60 * 1_000L
    private const val FALLBACK_LIMIT_MS = 24 * 60 * 60 * 1_000L
    private const val MAX_REFRESH_CONCURRENCY = 8
    private const val MAX_DISPLAY_ITEMS = 300

    private val gson = Gson()
    private val cacheLock = Any()
    private val cache = LinkedHashMap<String, ChannelCache>()
    private var cacheProfileId: String? = null
    private var refreshInProgress = false
    private var forceRefreshRequested = false

    internal var nowProvider: () -> Long = { System.currentTimeMillis() }
    internal var feedLoader: suspend (String) -> List<MediaItem>? = { fetchFeed(it) }

    @JvmStatic
    @JvmOverloads
    fun getFeed(vararg channelIds: String, type: Int = -1): MediaGroup? {
        val items = fetchFeeds(channelIds.toList())
        return createGroup(items, type)
    }

    @JvmStatic
    fun getSubscriptionFeed(type: Int = MediaGroup.TYPE_SUBSCRIPTIONS): MediaGroup? {
        val channelIds = subscribedChannelIds()
        if (channelIds.isEmpty()) return null
        return runBlocking {
            refreshSubscriptionChannels(channelIds, force = true)
            buildSnapshot(channelIds, type, includeExpired = false)
        }
    }

    @JvmStatic
    fun getSubscriptionFeedObserve(type: Int = MediaGroup.TYPE_SUBSCRIPTIONS): Observable<MediaGroup> {
        return RxHelper.create { emitter ->
            val channelIds = subscribedChannelIds()
            if (channelIds.isEmpty()) {
                emitter.onComplete()
                return@create
            }

            val force = synchronized(cacheLock) {
                ensureCacheLoadedLocked()
                val requested = forceRefreshRequested
                forceRefreshRequested = false
                requested
            }
            val initial = buildSnapshot(channelIds, type, includeExpired = false)
            if (initial != null) emitter.onNext(initial)

            if (!beginRefresh()) {
                if (initial == null) emitter.onComplete()
                else emitter.onComplete()
                return@create
            }

            try {
                val changed = runBlocking { refreshSubscriptionChannels(channelIds, force) }
                buildSnapshot(channelIds, type, includeExpired = false)?.let { refreshed ->
                    if (initial == null || changed) emitter.onNext(refreshed)
                }
                emitter.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                emitter.onComplete()
            } finally {
                endRefresh()
            }
        }
    }

    @JvmStatic
    fun requestSubscriptionRefresh() {
        synchronized(cacheLock) {
            forceRefreshRequested = true
        }
    }

    @JvmStatic
    fun hasCachedSubscriptionFeed(): Boolean {
        val ids = subscribedChannelIds()
        return ids.isNotEmpty() && buildSnapshot(ids, MediaGroup.TYPE_SUBSCRIPTIONS, includeExpired = false) != null
    }

    private fun subscribedChannelIds(): List<String> =
        ChannelGroupServiceImpl.getSubscribedChannelIds()?.toList()?.distinct().orEmpty()

    private suspend fun refreshSubscriptionChannels(channelIds: List<String>, force: Boolean): Boolean {
        val now = now()
        val toRefresh = synchronized(cacheLock) {
            ensureCacheLoadedLocked()
            channelIds.filter { channelId ->
                val entry = cache[channelId]
                force || entry == null || now - entry.fetchedAt > SOFT_TTL_MS
            }
        }

        if (toRefresh.isEmpty()) return false

        val updates = coroutineScope {
            toRefresh.chunked(MAX_REFRESH_CONCURRENCY).flatMap { batch ->
                batch.map { channelId ->
                    async(Dispatchers.IO) { channelId to feedLoader(channelId) }
                }.awaitAll()
            }
        }

        var changed = false
        synchronized(cacheLock) {
            ensureCacheLoadedLocked()
            updates.forEach { (channelId, items) ->
                if (items != null) {
                    cache[channelId] = ChannelCache(channelId, now(), items.map(::CachedMediaItem))
                    changed = true
                }
            }
            if (changed) persistCacheLocked()
        }
        return changed
    }

    private fun beginRefresh(): Boolean = synchronized(cacheLock) {
        if (refreshInProgress) return@synchronized false
        refreshInProgress = true
        true
    }

    private fun endRefresh() {
        synchronized(cacheLock) {
            refreshInProgress = false
        }
    }

    private fun buildSnapshot(channelIds: List<String>, type: Int, includeExpired: Boolean): MediaGroup? {
        val now = now()
        val items = synchronized(cacheLock) {
            ensureCacheLoadedLocked()
            channelIds.flatMap { channelId ->
                cache[channelId]?.takeIf {
                    includeExpired || now - it.fetchedAt <= FALLBACK_LIMIT_MS
                }?.items.orEmpty().map(CachedMediaItem::toMediaItem)
            }
        }
        if (items.isEmpty()) return null

        val deduplicated = LinkedHashMap<String, MediaItem>()
        val withoutId = mutableListOf<MediaItem>()
        items.forEach { item ->
            val videoId = item.videoId
            if (videoId == null) withoutId.add(item)
            else if (!deduplicated.containsKey(videoId)) deduplicated[videoId] = item
        }
        val merged = (deduplicated.values + withoutId)
            .sortedByDescending { it.publishedDate }
            .take(MAX_DISPLAY_ITEMS)
        return createGroup(merged, type)
    }

    private fun createGroup(items: List<MediaItem>, type: Int): MediaGroup =
        YouTubeMediaGroup(type).apply { mediaItems = items.toMutableList() }

    private fun fetchFeeds(channelIds: List<String>): MutableList<MediaItem> = runBlocking {
        val items = CopyOnWriteArrayList<MediaItem>()
        coroutineScope {
            channelIds.chunked(MAX_REFRESH_CONCURRENCY).forEach { batch ->
                batch.map { channelId ->
                    async(Dispatchers.IO) { fetchFeed(channelId)?.let(items::addAll) }
                }.awaitAll()
            }
        }
        items.toMutableList()
    }

    private suspend fun fetchFeed(channelId: String): List<MediaItem>? = withContext(Dispatchers.IO) {
        try {
            val rssContent = YouTubeInfoExtractor.downloadWebpage(RSS_URL + channelId)
            val result = YouTubeRssParser(Helpers.toStream(rssContent)).parse()
            syncWithChannel(channelId, result)
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Add missing props and remove shorts etc. */
    private fun syncWithChannel(channelId: String, result: List<MediaItem>) {
        val group = getBrowseService2().getChannelAsGrid(channelId)
        val originItems = group?.mediaItems ?: return

        Helpers.removeIf(result) { item ->
            val first = originItems.firstOrNull { it?.videoId == item.videoId }
            if (first != null) {
                item as YouTubeMediaItem
                item.badgeText = first.badgeText
                item.isLive = first.isLive
                item.isUpcoming = first.isUpcoming
                item.videoPreviewUrl = first.videoPreviewUrl
                item.percentWatched = first.percentWatched
                return@removeIf false
            }
            true
        }
    }

    private fun ensureCacheLoadedLocked() {
        val profileId = LocalProfileManager.instance().activeId
        if (cacheProfileId == profileId) return
        cache.clear()
        cacheProfileId = profileId
        val data = MediaServicePrefs.getProfileData(CACHE_KEY) ?: return
        try {
            val state = gson.fromJson(data, CacheState::class.java)
            state?.channels.orEmpty().forEach { cache[it.channelId] = it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistCacheLocked() {
        MediaServicePrefs.setProfileData(CACHE_KEY, gson.toJson(CacheState(cache.values.toList())))
    }

    private fun now(): Long = nowProvider()

    internal fun resetForTests() {
        synchronized(cacheLock) {
            cache.clear()
            cacheProfileId = null
            refreshInProgress = false
            forceRefreshRequested = false
            nowProvider = { System.currentTimeMillis() }
            feedLoader = { fetchFeed(it) }
            MediaServicePrefs.setProfileData(CACHE_KEY, null)
        }
    }

    internal fun reloadForTests() {
        synchronized(cacheLock) {
            cache.clear()
            cacheProfileId = null
            refreshInProgress = false
            forceRefreshRequested = false
        }
    }

    private fun getBrowseService2(): BrowseService2 = BrowseService2Wrapper

    private data class CacheState(val channels: List<ChannelCache> = emptyList())
    private data class ChannelCache(
        val channelId: String = "",
        val fetchedAt: Long = 0,
        val items: List<CachedMediaItem> = emptyList())

    private data class CachedMediaItem(
        val videoId: String? = null,
        val channelId: String? = null,
        val title: String? = null,
        val secondTitle: String? = null,
        val cardImageUrl: String? = null,
        val badgeText: String? = null,
        val productionDate: String? = null,
        val publishedDate: Long = 0,
        val author: String? = null,
        val videoPreviewUrl: String? = null,
        val percentWatched: Int = -1,
        val isLive: Boolean = false,
        val isUpcoming: Boolean = false,
        val isMovie: Boolean = false) {
        constructor(item: MediaItem) : this(
            item.videoId, item.channelId, item.title, item.secondTitle?.toString(),
            item.cardImageUrl, item.badgeText, item.productionDate, item.publishedDate,
            item.author, item.videoPreviewUrl, item.percentWatched, item.isLive,
            item.isUpcoming, item.isMovie)

        fun toMediaItem(): MediaItem = YouTubeMediaItem().apply {
            videoId = this@CachedMediaItem.videoId
            channelId = this@CachedMediaItem.channelId
            title = this@CachedMediaItem.title
            secondTitle = this@CachedMediaItem.secondTitle
            cardImageUrl = this@CachedMediaItem.cardImageUrl
            badgeText = this@CachedMediaItem.badgeText
            author = this@CachedMediaItem.author
            videoPreviewUrl = this@CachedMediaItem.videoPreviewUrl
            percentWatched = this@CachedMediaItem.percentWatched
            isLive = this@CachedMediaItem.isLive
            isUpcoming = this@CachedMediaItem.isUpcoming
            isMovie = this@CachedMediaItem.isMovie
            publishedDate = this@CachedMediaItem.publishedDate
        }
    }
}
