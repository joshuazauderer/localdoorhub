package com.localdoorhub.app.data.models

data class DoorbellEvent(
    val id: String,
    val type: String,
    val timestamp: String,
    val thumbnailUrl: String?
)

