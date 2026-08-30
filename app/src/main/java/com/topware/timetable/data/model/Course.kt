package com.topware.timetable.data.model

import java.io.Serializable

data class Course(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val teacher: String = "",
    val dayOfWeek: Int, // 1 (Mon) to 7 (Sun)
    val dayName: String = "",
    val jieci: String = "",
    val startPeriod: Int,
    val endPeriod: Int,
    val periodCount: Int = endPeriod - startPeriod + 1,
    val weeksStr: String = "",
    val weeks: List<Int> = emptyList(),
    val location: String = "",
    val department: String = "",
    val phone: String = "",
    val colorIndex: Int = 0
) : Serializable {

    fun isHappeningInWeek(weekNumber: Int): Boolean {
        return weeks.isEmpty() || weeks.contains(weekNumber)
    }

    fun getFormattedTime(): String {
        val start = TimeSlot.getStartTime(startPeriod)
        val end = TimeSlot.getEndTime(endPeriod)
        return "$start - $end"
    }
}
