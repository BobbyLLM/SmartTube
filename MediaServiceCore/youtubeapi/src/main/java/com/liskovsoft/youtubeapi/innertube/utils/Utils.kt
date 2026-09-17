package com.liskovsoft.youtubeapi.innertube.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.liskovsoft.sharedutils.helpers.Helpers
import java.util.regex.Pattern

/**
 * Finds a string between two delimiters.
 * @param data the data
 * @param startString start string
 * @param endString end string
 * @return the string between start and end, or null if not found
 */
internal fun getStringBetweenStrings(data: String, startString: String, endString: String): String? {
    val regex = escapeStringRegexp(startString) + "(.*?)" + escapeStringRegexp(endString)
    val pattern = Pattern.compile(regex, Pattern.DOTALL)
    val matcher = pattern.matcher(data)
    if (matcher.find()) {
        return matcher.group(1)
    } else {
        return null
    }
}

/**
 * Escapes a string to be used in a regex.
 * @param input input string
 * @return escaped string
 */
internal fun escapeStringRegexp(input: String): String {
    // Escape special regex characters
    val escaped = input.replace("([|\\\\{}()\\[\\]^$+*?.])".toRegex(), "\\\\$1")
    // Replace dash
    return escaped.replace("-", "\\x2d")
}

internal enum class DeviceCategory {
    MOBILE,
    DESKTOP
}

internal fun UserAgents.byCategory(category: DeviceCategory): List<String> =
    when (category) {
        DeviceCategory.DESKTOP -> desktop
        DeviceCategory.MOBILE -> mobile
    }

internal fun getRandomUserAgent(type: DeviceCategory): String {
    return UserAgents
        .byCategory(type)
        .random()
}

internal fun toJsonString(obj: Any): String {
    val gson = GsonBuilder().create() // nulls are ignored by default
    return gson.toJson(obj)
}


/**
 * Generates a random string with the given length.
 *
 */
internal fun generateRandomString(length: Int): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val result = StringBuilder(length)

    repeat(length) {
        result.append(alphabet[Helpers.getRandom().nextInt(alphabet.length)])
    }

    return result.toString()
}

/**
 * Extracts props from complex json e.g.
 *
 * ```
 * val parser = JsonParser()
 * val root = parser.parse(ytCfgStr?.ytCfg).asJsonObject
 *
 * val encryptedHostFlags = traverse(
 *             root,
 *             "WEB_PLAYER_CONTEXT_CONFIGS",
 *             "WEB_PLAYER_CONTEXT_CONFIG_ID_EMBEDDED_PLAYER",
 *             "encryptedHostFlags"
 *         )?.asString
 * ```
 */
internal fun traverseObj(root: JsonElement?, vararg path: String): JsonElement? {
    var current = root ?: return null
    var i = 0

    while (i < path.size) {
        val key = path[i]

        if (key == "...") {
            val nextKey = path.getOrNull(i + 1) ?: return current
            current = findDeep(current, nextKey) ?: return null
            i += 2
            continue
        }

        current = resolveObj(current, key) ?: return null
        i++
    }

    return current
}

private fun resolveObj(el: JsonElement, key: String): JsonElement? {
    // array index support
    key.toIntOrNull()?.let { index ->
        if (el.isJsonArray) {
            val arr = el.asJsonArray
            return if (index in 0 until arr.size()) arr[index] else null
        }
        return null
    }

    // object key
    if (el.isJsonObject) {
        return el.asJsonObject.get(key)
    }

    return null
}

private fun findDeep(el: JsonElement?, key: String): JsonElement? {
    if (el == null) return null

    if (el.isJsonObject) {
        val obj = el.asJsonObject

        obj.get(key)?.let { return it }

        for ((_, value) in obj.entrySet()) {
            findDeep(value, key)?.let { return it }
        }
    } else if (el.isJsonArray) {
        for (item in el.asJsonArray) {
            findDeep(item, key)?.let { return it }
        }
    }

    return null
}