package com.ra2.webonphone.data

import kotlinx.serialization.Serializable

@Serializable
data class LinkItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val isDefault: Boolean = false
)
