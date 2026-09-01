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

    /**
     * 计算指定周包含的 7 天具体公历日期（月、日）
     */
    fun getWeekDates(config: SemesterConfig, week: Int): List<Pair<Int, Int>> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = config.startDateMillis
            add(Calendar.DAY_OF_YEAR, (week - 1) * 7)
        }
        val list = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 7) {
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            list.add(Pair(m, d))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    /**
     * 获取指定周的主月份名称，如 "9月" 或 "8月"
     */
    fun getMonthNameForWeek(config: SemesterConfig, week: Int): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = config.startDateMillis
            add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + 3) // 取周中（周四）作为主月
        }
        val m = cal.get(Calendar.MONTH) + 1
        return "${m}月"
    }

    /**
     * 获取指定周的具体公历日期跨度字符串，如 "8月31日 - 9月6日"
     */
    fun getWeekDateRange(config: SemesterConfig, week: Int): String {
        val dates = getWeekDates(config, week)
        if (dates.isEmpty()) return ""
        val start = dates.first()
        val end = dates.last()
        return "${start.first}月${start.second}日 - ${end.first}月${end.second}日"
    }

    /**
     * 获取指定周某一天（1..7）的具体日期字符串，如 "9月1日"
     */
    fun getSpecificDateString(config: SemesterConfig, week: Int, dayOfWeek: Int): String {
        val dates = getWeekDates(config, week)
        val idx = (dayOfWeek - 1).coerceIn(0, 6)
        if (idx < dates.size) {
            val (m, d) = dates[idx]
            return "${m}月${d}日"
        }
        return ""
    }

    /**
     * 获取今日完整日期显示，如 "9月1日 周二"
     */
    fun getTodayFullDateString(): String {
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val dayName = getDayName(getCurrentDayOfWeek())
        return "${m}月${d}日 $dayName"
    }
}
