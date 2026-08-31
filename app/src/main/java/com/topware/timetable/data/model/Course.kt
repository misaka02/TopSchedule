package com.topware.timetable.data.model

import java.io.Serializable
import java.util.Calendar

enum class CourseStatus {
    ONGOING,       // 正在进行
    NEXT_UPCOMING, // 下一节即将开始
    FUTURE,        // 今日后续课程
    FINISHED       // 今日已结束
}

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
    val teacherAssignments: List<TeacherAssignment> = emptyList(),
    val location: String = "",
    val department: String = "",
    val phone: String = "",
    val colorIndex: Int = 0
) : Serializable {

    fun isHappeningInWeek(weekNumber: Int): Boolean {
        return weeks.isEmpty() || weeks.contains(weekNumber)
    }

    /**
     * 获取指定周次对应的任课教师
     */
    fun getTeacherForWeek(weekNumber: Int): String {
        val assignment = teacherAssignments.firstOrNull { it.weeks.contains(weekNumber) }
        return if (assignment != null && assignment.teacherName.isNotBlank()) {
            assignment.teacherName
        } else {
            teacher.ifBlank { "待定" }
        }
    }

    fun getStartTime(): String {
        return TimeSlot.getStartTime(startPeriod)
    }

    fun getEndTime(): String {
        return TimeSlot.getEndTime(endPeriod)
    }

    fun getFormattedTimeRange(): String {
        return "${getStartTime()} - ${getEndTime()}"
    }

    fun getFormattedPeriodRange(): String {
        return if (startPeriod == endPeriod) "第 $startPeriod 节" else "第 $startPeriod-$endPeriod 节"
    }

    fun calculateStatus(cal: Calendar = Calendar.getInstance()): CourseStatus {
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = TimeSlot.parseTimeToMinutes(getStartTime())
        val endMinutes = TimeSlot.parseTimeToMinutes(getEndTime())

        return when {
            currentMinutes in startMinutes..endMinutes -> CourseStatus.ONGOING
            currentMinutes < startMinutes -> CourseStatus.FUTURE
            else -> CourseStatus.FINISHED
        }
    }

    fun getMinutesUntilStart(cal: Calendar = Calendar.getInstance()): Int {
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = TimeSlot.parseTimeToMinutes(getStartTime())
        return startMinutes - currentMinutes
    }
}
