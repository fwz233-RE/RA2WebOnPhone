package com.ra2.webonphone.data

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val version: String,
    val updateContent: String,
    val domesticUrl: String,
    val internationalUrl: String
)
