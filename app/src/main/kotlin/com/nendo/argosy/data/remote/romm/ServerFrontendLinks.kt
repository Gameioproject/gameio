package com.nendo.argosy.data.remote.romm

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun normalizeSupportUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.rawUserInfo != null) return null
    val url = value.toHttpUrlOrNull() ?: return null
    return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
}
