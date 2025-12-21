package com.ra2.webonphone.data

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class LinkItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val isDefault: Boolean = false,
    val iconUrl: String? = null
) {
    fun getFaviconUrl(): String {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return ""
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        } catch (e: Exception) {
            ""
        }
    }
}
