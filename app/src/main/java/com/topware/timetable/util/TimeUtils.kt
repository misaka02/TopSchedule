package com.topware.timetable.util

import com.topware.timetable.data.model.SemesterConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun getCurrentWeek(config: SemesterConfig): Int {
        val now = System.currentTimeMillis()
        val start = config.startDateMillis
        if (now < start) {
            return 1
        }
        val diffDays = ((now - start) / (1000 * 60 * 60 * 24)).toInt()
        val week = (diffDays / 7) + 1
        return week.coerceIn(1, config.totalWeeks)
    }

    fun getCurrentDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "周一"
        }
    }

    fun getFormattedDate(dateMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return sdf.format(Date(dateMillis))
    }
}
