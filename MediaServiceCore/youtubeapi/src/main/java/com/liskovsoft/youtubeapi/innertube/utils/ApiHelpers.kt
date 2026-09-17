package com.liskovsoft.youtubeapi.innertube.utils

import com.liskovsoft.youtubeapi.innertube.models.InnertubeContext
import com.liskovsoft.youtubeapi.innertube.models.SessionArgs
import com.liskovsoft.youtubeapi.innertube.models.SessionDataResult

internal object ApiHelpers {
    fun createSessionDataHeaders(options: SessionArgs): Map<String, String> {
        val lang = options.lang ?: "en-US"
        val visitorId = generateRandomString(11)
        // "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 OPR/121.0.0.0"

        // The other headers: com.liskovsoft.youtubeapi.innertube.core.SessionApi
        return mapOf(
            "Accept-Language" to lang,
            "User-Agent" to options.userAgent,
            "Accept" to "*/*",
            "Referer" to "${URLS.YT_BASE}/sw.js",
            "Cookie" to "PREF=tz=${options.timeZone.replace('/', '.')};VISITOR_INFO1_LIVE=$visitorId;"
        )
    }

    fun createInnertubeConfigHeaders(sessionData: SessionDataResult): Map<String, String> {
        // The other headers: com.liskovsoft.youtubeapi.innertube.core.InnertubeConfigApi
        return buildMap {
            put("Accept-Language", "") // TODO: replace with the lang param
            put("Accept", "*/*")
            put("Referer", URLS.YT_BASE)
            put("X-Origin", URLS.YT_BASE)

            sessionData.ytcfg?.deviceInfo?.visitorData
                ?.let { put("X-Goog-Visitor-Id", it) }

            sessionData.ytcfg?.deviceInfo?.clientVersion
                ?.let { put("X-Youtube-Client-Version", it) }
        }
    }

    fun createInnertubeJsonConfig(innertubeContext: InnertubeContext): String {
        return toJsonString(mapOf("context" to innertubeContext))
    }
}