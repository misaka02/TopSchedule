package com.topware.timetable.data.model

import java.io.Serializable

data class TeacherAssignment(
    val startWeek: Int,
    val endWeek: Int,
    val weeks: List<Int>,
    val teacherName: String,
    val rawWeeksStr: String = ""
) : Serializable
