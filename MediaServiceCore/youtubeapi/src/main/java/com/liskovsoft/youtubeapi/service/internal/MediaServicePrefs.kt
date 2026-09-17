package com.liskovsoft.youtubeapi.service.internal

import android.annotation.SuppressLint
import com.liskovsoft.sharedutils.misc.WeakHashSet
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase
import com.liskovsoft.youtubeapi.app.AppService

private const val PREF_NAME = "yt_service_prefs"

@SuppressLint("StaticFieldLeak")
internal object MediaServicePrefs: SharedPreferencesBase(AppService.instance().context, PREF_NAME), LocalProfileManager.Listener {
    private val mListeners = WeakHashSet<ProfileChangeListener>()

    interface ProfileChangeListener {
        fun onProfileChanged()
    }

    init {
        LocalProfileManager.instance().addListener(this)
    }

    override fun onProfileChanged() {
        notifyListeners()
    }

    private fun notifyListeners() {
        mListeners.forEach { it.onProfileChanged() }
    }

    fun addListener(listener: ProfileChangeListener) {
        mListeners.add(listener)
    }

    //fun getData(key: String): String? {
    //    return getString(getProfileDataKey(key), null)
    //}
    //
    //fun setData(key: String, data: String?) {
    //    putString(getProfileDataKey(key), data)
    //}

    fun getProfileData(key: String): String? {
        return getData(getProfileDataKey(key))
    }

    fun setProfileData(key: String, data: String?) {
        setData(getProfileDataKey(key), data)
    }

    private fun getProfileDataKey(dataKey: String): String {
        val profileKey = "local_${LocalProfileManager.instance().activeId}"
        val currentKey = "${profileKey}_$dataKey"
        // Preserve the pre-profile local data on first launch.
        return if (getData(currentKey) == null && getData("anonymous_$dataKey") != null) {
            setData(currentKey, getData("anonymous_$dataKey"))
            currentKey
        } else currentKey
    }

    override fun getPrefsDir(): String = PREF_NAME
}
