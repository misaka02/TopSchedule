package com.topware.timetable.data.model

data class TimeSlot(
    val period: Int,
    val startTime: String,
    val endTime: String
) {
    companion object {
        val DEFAULT_PERIODS = listOf(
            TimeSlot(1, "08:00", "08:45"),
            TimeSlot(2, "08:50", "09:35"),
            TimeSlot(3, "09:55", "10:40"),
            TimeSlot(4, "10:45", "11:30"),
            TimeSlot(5, "11:35", "12:20"),
            TimeSlot(6, "13:30", "14:15"),
            TimeSlot(7, "14:20", "15:05"),
            TimeSlot(8, "15:25", "16:10"),
            TimeSlot(9, "16:15", "17:00"),
            TimeSlot(10, "17:05", "17:50"),
            TimeSlot(11, "18:30", "19:15"),
            TimeSlot(12, "19:20", "20:05"),
            TimeSlot(13, "20:10", "20:55")
        )

        fun getStartTime(period: Int): String {
            return DEFAULT_PERIODS.firstOrNull { it.period == period }?.startTime ?: "08:00"
        }

        fun getEndTime(period: Int): String {
            return DEFAULT_PERIODS.firstOrNull { it.period == period }?.endTime ?: "20:55"
        }

        fun parseTimeToMinutes(timeStr: String): Int {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                return h * 60 + m
            }
            return 0
        }
    }
}
