package com.ra2.webonphone.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LinkRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFS_NAME = "ra2_links_prefs"
        private const val KEY_LINKS = "saved_links"
        private const val KEY_INITIALIZED = "initialized"

        val DEFAULT_LINKS = listOf(
            LinkItem(
                id = "default_1",
                title = "RA2Github",
                url = "https://ra2web.github.io/",
                isDefault = true
            ),
            LinkItem(
                id = "default_2",
                title = "RA2官网",
                url = "https://game.ra2web.com/",
                isDefault = true
            ),
            LinkItem(
                id = "default_3",
                title = "RA2内测",
                url = "https://staging.wangerhuoda.com",
                isDefault = true
            )
        )
    }

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            saveLinks(DEFAULT_LINKS)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        }
    }

    fun getLinks(): List<LinkItem> {
        val linksJson = prefs.getString(KEY_LINKS, null) ?: return DEFAULT_LINKS
        return try {
            json.decodeFromString<List<LinkItem>>(linksJson)
        } catch (e: Exception) {
            DEFAULT_LINKS
        }
    }

    fun saveLinks(links: List<LinkItem>) {
        val linksJson = json.encodeToString(links)
        prefs.edit().putString(KEY_LINKS, linksJson).apply()
    }

    fun addLink(title: String, url: String) {
        val currentLinks = getLinks().toMutableList()
        currentLinks.add(LinkItem(title = title, url = url))
        saveLinks(currentLinks)
    }

    fun removeLink(linkId: String) {
        val currentLinks = getLinks().toMutableList()
        currentLinks.removeAll { it.id == linkId && !it.isDefault }
        saveLinks(currentLinks)
    }
}
