package com.topware.timetable.data.model

import java.io.Serializable

data class SemesterConfig(
    val semesterName: String = "当前学期",
    val startDateMillis: Long = 1788105600000L, // 2026-08-31
    val totalWeeks: Int = 20
) : Serializable
