package com.liskovsoft.youtubeapi.playlistgroups

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.helpers.Helpers
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.youtubeapi.channelgroups.models.ItemGroupImpl
import com.liskovsoft.youtubeapi.channelgroups.models.ItemImpl
import com.liskovsoft.youtubeapi.service.internal.MediaServicePrefs
import io.reactivex.disposables.Disposable

object PlaylistGroupServiceImpl : MediaServicePrefs.ProfileChangeListener {
    private const val PLAYLIST_GROUP_DATA = "playlist_group_data"
    private const val PERSIST_DELAY_MS: Long = 5_000
    private lateinit var mPlaylists: MutableList<ItemGroup>
    private var mPersistAction: Disposable? = null
    @JvmField
    var cachedItem: MediaItem? = null

    init {
        MediaServicePrefs.addListener(this)
        restoreData()
    }

    override fun onProfileChanged() {
        restoreData()
    }

    @JvmStatic
    fun addPlaylistGroup(group: ItemGroup?) {
        if (group == null)
            return

        // Move to the top
        (group as ItemGroupImpl).onChange = { persistData() }
        mPlaylists.remove(group)
        mPlaylists.add(0, group)
        persistData()
    }

    @JvmStatic
    fun removePlaylistGroup(group: ItemGroup) {
        mPlaylists.remove(group)
        persistData()
    }

    @JvmStatic
    fun removePlaylistGroup(id: String?) {
        if (id == null)
            return

        val result = Helpers.removeIf(mPlaylists) { it.id == id }
        if (!result.isNullOrEmpty()) {
            persistData()
        }
    }

    @JvmStatic
    fun renamePlaylistGroup(itemGroup: ItemGroup, title: String) {
        addPlaylistGroup(ItemGroupImpl(itemGroup.id, title, itemGroup.iconUrl, itemGroup.items))
    }

    @JvmStatic
    fun createPlaylistGroup(title: String, iconUrl: String?, items: List<Item>): ItemGroup {
        return ItemGroupImpl(title = title, iconUrl = iconUrl, items = items.toMutableList())
    }

    @JvmStatic
    fun createPlaylistGroup(id: String, title: String, iconUrl: String?): ItemGroup {
        return ItemGroupImpl(id = id, title = title, iconUrl = iconUrl, items = mutableListOf())
    }

    @JvmStatic
    fun getPlaylistGroups(): List<ItemGroup> {
        return mPlaylists
    }

    /** Merge a complete, already validated Takeout import into the active profile once. */
    @JvmStatic
    fun mergeTakeoutPlaylists(imported: List<TakeoutPlaylist>) {
        val merged = mPlaylists.toMutableList()

        imported.forEach { playlist ->
            val items: MutableList<Item> = playlist.items.map {
                ItemImpl(it.channelId, it.title, it.iconUrl, it.videoId, it.subtitle, it.badge)
            }.toMutableList()
            val group = ItemGroupImpl(playlist.id, playlist.title, null, items)
            val existingIndex = merged.indexOfFirst { it.id == playlist.id }
            if (existingIndex >= 0) {
                merged[existingIndex] = group
            } else {
                merged.add(group)
            }
        }

        mPlaylists = merged
        mPlaylists.forEach {
            it as ItemGroupImpl
            it.onChange = { persistData() }
        }
        MediaServicePrefs.setProfileData(PLAYLIST_GROUP_DATA, Helpers.mergeData(mPlaylists))
    }

    /** Replace the active local profile's playlists with one validated import payload. */
    @JvmStatic
    fun importData(data: String?): Boolean {
        if (data.isNullOrEmpty()) return false

        val imported = Helpers.parseList(Helpers.splitData(data), 0, ItemGroupImpl::fromString)
        if (imported.isNullOrEmpty()) return false

        MediaServicePrefs.setProfileData(PLAYLIST_GROUP_DATA, data)
        mPlaylists = imported
        mPlaylists.forEach {
            it as ItemGroupImpl
            it.onChange = { persistData() }
        }
        return true
    }

    @JvmStatic
    fun findPlaylistGroup(id: String?): ItemGroup? {
        if (id == null)
            return null

        return mPlaylists.firstOrNull { it.id == id }
    }

    @JvmStatic
    fun findPlaylistGroupByTitle(title: String?): ItemGroup? {
        if (title == null)
            return null

        return mPlaylists.firstOrNull { it.title == title }
    }

    private fun restoreData() {
        val data = MediaServicePrefs.getProfileData(PLAYLIST_GROUP_DATA)
        restoreData(data)
    }

    private fun restoreData(data: String?) {
        val split = Helpers.splitData(data)

        mPlaylists = Helpers.parseList(split, 0, ItemGroupImpl::fromString)
        mPlaylists.forEach {
            it as ItemGroupImpl
            it.onChange = { persistData() }
        }
    }

    private fun persistData() {
        RxHelper.disposeActions(mPersistAction)
        mPersistAction = RxHelper.runAsync(::persistDataReal, PERSIST_DELAY_MS)
    }

    private fun persistDataReal() {
        MediaServicePrefs.setProfileData(PLAYLIST_GROUP_DATA, Helpers.mergeData(mPlaylists))
    }
}
