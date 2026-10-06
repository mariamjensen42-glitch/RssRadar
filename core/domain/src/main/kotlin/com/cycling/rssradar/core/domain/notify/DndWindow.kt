package com.cycling.rssradar.core.domain.notify

object DndWindow {

    const val MINUTES_PER_DAY = 24 * 60

    fun isQuiet(nowMinute: Int, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        val now = normalize(nowMinute)
        val start = normalize(startMinute)
        val end = normalize(endMinute)
        return if (start < end) now >= start && now < end else now >= start || now < end
    }

    fun normalize(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    fun minuteOfDay(epochMillis: Long, zoneOffsetMillis: Int): Int {
        val dayMillis = 24L * 60L * 60L * 1000L
        val local = epochMillis + zoneOffsetMillis
        val withinDay = ((local % dayMillis) + dayMillis) % dayMillis
        return (withinDay / 60000L).toInt()
    }
}
