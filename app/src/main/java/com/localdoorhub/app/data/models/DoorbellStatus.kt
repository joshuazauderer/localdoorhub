package com.localdoorhub.app.data.models

data class DoorbellStatus(
    val doorbellOnline: Boolean,
    val doorName: String?,
    val batteryPercent: Int?,
    val wifiStrength: String?,
    val rtspUrl: String?
)

