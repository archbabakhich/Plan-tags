package de.lionsun.plantags.models

data class HolidayMetadata(
    val version: String,
    val countries: List<HolidayInfo>
)
